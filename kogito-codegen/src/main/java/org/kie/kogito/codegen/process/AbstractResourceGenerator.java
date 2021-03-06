/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.kogito.codegen.process;

import static com.github.javaparser.StaticJavaParser.parse;
import static org.kie.kogito.codegen.process.CodegenUtils.interpolateTypes;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BinaryExpr.Operator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import org.drools.core.util.StringUtils;
import org.jbpm.compiler.canonical.UserTaskModelMetaData;
import org.kie.api.definition.process.WorkflowProcess;
import org.kie.kogito.codegen.BodyDeclarationComparator;
import org.kie.kogito.codegen.GeneratorContext;
import org.kie.kogito.codegen.di.DependencyInjectionAnnotator;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.impl.Sig;

/**
 * AbstractResourceGenerator
 */
public abstract class AbstractResourceGenerator {

    private final String relativePath;

    private final GeneratorContext context;
    private WorkflowProcess process;
    private final String packageName;
    private final String resourceClazzName;
    private final String processClazzName;
    private String processId;
    private String dataClazzName;
    private String modelfqcn;
    private final String processName;
    private final String appCanonicalName;
    private DependencyInjectionAnnotator annotator;
    
    private boolean startable;
    private List<UserTaskModelMetaData> userTasks;
    private Map<String, String> signals;

    public AbstractResourceGenerator(
            GeneratorContext context, 
            WorkflowProcess process,
            String modelfqcn,
            String processfqcn,
            String appCanonicalName) {
        this.context = context;
        this.process = process;
        this.packageName = process.getPackageName();
        this.processId = process.getId();
        this.processName = processId.substring(processId.lastIndexOf('.') + 1);
        this.appCanonicalName = appCanonicalName;
        String classPrefix = StringUtils.capitalize(processName);
        this.resourceClazzName = classPrefix + "Resource";
        this.relativePath = packageName.replace(".", "/") + "/" + resourceClazzName + ".java";
        this.modelfqcn = modelfqcn;
        this.dataClazzName = modelfqcn.substring(modelfqcn.lastIndexOf('.') + 1);
        this.processClazzName = processfqcn;
    }

    public AbstractResourceGenerator withDependencyInjection(DependencyInjectionAnnotator annotator) {
        this.annotator = annotator;
        return this;
    }
    
    public AbstractResourceGenerator withUserTasks(List<UserTaskModelMetaData> userTasks) {
        this.userTasks = userTasks;
        return this;
    }
    
    public AbstractResourceGenerator withSignals(Map<String, String> signals) {
        this.signals = signals;
        return this;
    }
    
    public AbstractResourceGenerator withTriggers(boolean startable) {
        this.startable = startable;
        return this;
    }

    public String className() {
        return resourceClazzName;
    }

    protected abstract String getResourceTemplate();

    public String generate() {
        CompilationUnit clazz = parse(
                this.getClass().getResourceAsStream(getResourceTemplate()));
        clazz.setPackageDeclaration(process.getPackageName());
        clazz.addImport(modelfqcn);
        clazz.addImport(modelfqcn + "Output");

        ClassOrInterfaceDeclaration template = clazz
                .findFirst(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() -> new NoSuchElementException("Compilation unit doesn't contain a class or interface declaration!"));

        template.setName(resourceClazzName);
        
        if (signals != null) {
            
            int index = 0;
            for (Entry<String, String> entry : signals.entrySet()) {
                MethodDeclaration signalMethod = new MethodDeclaration()
                        .setName("signal_" + index++)
                        .setType(modelfqcn + "Output")
                        .setModifiers(Keyword.PUBLIC)
                        .addAnnotation("POST")
                        .addSingleMemberAnnotation("Path", new StringLiteralExpr("/{id}/" + entry.getKey()))
                        .addSingleMemberAnnotation("Produces", "MediaType.APPLICATION_JSON");
                
                signalMethod.addAndGetParameter("String", "id").addSingleMemberAnnotation("PathParam", new StringLiteralExpr("id"));
                
                if (entry.getValue() != null) {
                    signalMethod.addSingleMemberAnnotation("Consumes", "MediaType.APPLICATION_JSON");                    
                    signalMethod.addAndGetParameter(entry.getValue(), "data");
                }
                
                // method body to signal process instance                
                MethodCallExpr newSignal = new MethodCallExpr(new NameExpr(Sig.class.getCanonicalName()), "of")
                        .addArgument(new StringLiteralExpr(entry.getKey()))
                        .addArgument(entry.getValue() != null ? new NameExpr("data") : new NullLiteralExpr());
                MethodCallExpr instances = new MethodCallExpr(new NameExpr("process"), "instances");
                MethodCallExpr findById = new MethodCallExpr(instances, "findById").addArgument(new NameExpr("id"));
                MethodCallExpr getOptional = new MethodCallExpr(findById, "orElse").addArgument(new NullLiteralExpr());
                
                VariableDeclarator processInstance = new VariableDeclarator(new ClassOrInterfaceType(null, new SimpleName(ProcessInstance.class.getCanonicalName()), 
                                                                                                     NodeList.nodeList(new ClassOrInterfaceType(null, modelfqcn))),
                                                                                                     "pi",
                                                                                                     getOptional);
                // local variable for process instance
                VariableDeclarationExpr processInstanceField = new VariableDeclarationExpr(processInstance);
                // signal only when there is non null process instance
                IfStmt processInstanceExists = new IfStmt(new BinaryExpr(new NameExpr("pi"), new NullLiteralExpr(), Operator.EQUALS), 
                                                new ReturnStmt(new NullLiteralExpr()), 
                                                 null);
                
                MethodCallExpr send = new MethodCallExpr(new NameExpr("pi"), "send").addArgument(newSignal);
                // return current state of variables after the signal
                MethodCallExpr variables = new MethodCallExpr("getModel").addArgument(new NameExpr("pi"));
                signalMethod.createBody().addStatement(processInstanceField).addStatement(processInstanceExists).addStatement(send).addStatement(new ReturnStmt(variables));  
                
                
                template.addMember(signalMethod);
            }
        }
        // security must be applied before user tasks are added to make sure that user task
        // endpoints are not security annotated as they should restrict access based on user assignments
        securityAnnotated(template);
        
        if (userTasks != null) {

            CompilationUnit userTaskClazz = parse(this.getClass().getResourceAsStream("/class-templates/RestResourceUserTaskTemplate.java"));
            
            
            ClassOrInterfaceDeclaration userTaskTemplate = userTaskClazz
                    .findFirst(ClassOrInterfaceDeclaration.class)
                    .orElseThrow(() -> new NoSuchElementException("Compilation unit doesn't contain a class or interface declaration!"));
            for (UserTaskModelMetaData userTask : userTasks) {
       
                userTaskTemplate.findAll(MethodDeclaration.class).forEach(md -> {                    
                    
                    MethodDeclaration cloned = md.clone();
                    template.addMethod(cloned.getName() + "_" + userTask.getId(), Keyword.PUBLIC)
                    .setType(cloned.getType())
                    .setParameters(cloned.getParameters())
                    .setBody(cloned.getBody().get())
                    .setAnnotations(cloned.getAnnotations());
                    
                });
                
                template.findAll(StringLiteralExpr.class).forEach(s -> interpolateUserTaskStrings(s, userTask));
                
                template.findAll(ClassOrInterfaceType.class).forEach(c -> interpolateUserTaskTypes(c, userTask.getInputMoodelClassSimpleName(), userTask.getOutputMoodelClassSimpleName()));
                template.findAll(NameExpr.class).forEach(c -> interpolateUserTaskNameExp(c, userTask));
                
            }
        }
        
        template.findAll(StringLiteralExpr.class).forEach(this::interpolateStrings);
        Map<String, String> typeInterpolations = new HashMap<>();
        typeInterpolations.put("$Clazz$", resourceClazzName);
        typeInterpolations.put("$Type$", dataClazzName);
        template.findAll(ClassOrInterfaceType.class).forEach(cls -> interpolateTypes(cls, typeInterpolations));
        template.findAll(MethodDeclaration.class).forEach(this::interpolateMethods);

        if (useInjection()) {
            template.findAll(FieldDeclaration.class,
                             CodegenUtils::isProcessField).forEach(fd -> annotator.withNamedInjection(fd, processId));
            
            template.findAll(FieldDeclaration.class,
                             CodegenUtils::isApplicationField).forEach(fd -> annotator.withInjection(fd));
        } else {
            template.findAll(FieldDeclaration.class,
                             CodegenUtils::isProcessField).forEach(this::initializeProcessField);
            
            template.findAll(FieldDeclaration.class,
                             CodegenUtils::isApplicationField).forEach(this::initializeApplicationField);
        }
        
        // if triggers are not empty remove createResource method as there is another trigger to start process instances
        if (!startable || !isPublic()) {
            Optional<MethodDeclaration> createResourceMethod = template.findFirst(MethodDeclaration.class).filter(md -> md.getNameAsString().equals("createResource_" + processName));
            if (createResourceMethod.isPresent()) {
                template.remove(createResourceMethod.get());
            }
        }
        
        if (useInjection()) {
            annotator.withApplicationComponent(template);
        }
        
        enableValidation(template);
        
        template.getMembers().sort(new BodyDeclarationComparator());
        return clazz.toString();
    }

    
    private void securityAnnotated(ClassOrInterfaceDeclaration template) {
        if (useInjection() && process.getMetaData().containsKey("securityRoles")) {
            String[] roles = ((String) process.getMetaData().get("securityRoles")).split(",");
            template.findAll(MethodDeclaration.class).stream().filter(this::requiresSecurity).forEach(md -> annotator.withSecurityRoles(md, roles));
        }
    }
    
    private boolean requiresSecurity(MethodDeclaration md) {
        // applies to only rest annotated methods
        return md.getAnnotationByName("POST").isPresent() ||
                md.getAnnotationByName("GET").isPresent() ||
                md.getAnnotationByName("PUT").isPresent() ||
                md.getAnnotationByName("DELETE").isPresent();           
    }
    
    private void enableValidation(ClassOrInterfaceDeclaration template) {
        if (context.getBuildContext().isValidationSupported()) {
            template.findAll(Parameter.class).stream().filter(param -> param.getTypeAsString().equals(dataClazzName+"Input")).forEach(param -> {
                param.addAnnotation("javax.validation.Valid");
                param.addAnnotation("javax.validation.constraints.NotNull");
                });
        }
    }

    private void initializeProcessField(FieldDeclaration fd) {
        fd.getVariable(0).setInitializer(new ObjectCreationExpr().setType(processClazzName));
    }
    
    private void initializeApplicationField(FieldDeclaration fd) {
        fd.getVariable(0).setInitializer(new ObjectCreationExpr().setType(appCanonicalName));
    }

    private void interpolateStrings(StringLiteralExpr vv) {
        String s = vv.getValue();
        String documentation =
                process.getMetaData()
                        .getOrDefault("Documentation", processName).toString();
        String interpolated =
                s.replace("$name$", processName)
                        .replace("$id$", processId)
                        .replace("$documentation$", documentation);
        vv.setString(interpolated);
    }
    
    private void interpolateUserTaskStrings(StringLiteralExpr vv, UserTaskModelMetaData userTask) {
        String s = vv.getValue();
   
        String interpolated =
                s.replace("$taskname$", userTask.getName().replaceAll("\\s", "_"));
        vv.setString(interpolated);
    }
    
    private void interpolateUserTaskNameExp(NameExpr name, UserTaskModelMetaData userTask) {        
        String identifier = name.getNameAsString();
        
        name.setName(identifier.replace("$TaskInput$", userTask.getInputMoodelClassSimpleName()));
        
        identifier = name.getNameAsString();
        name.setName(identifier.replace("$TaskOutput$", userTask.getOutputMoodelClassSimpleName()));
    }    
    
    private void interpolateMethods(MethodDeclaration m) {
        SimpleName methodName = m.getName();
        String interpolated =
                methodName.asString().replace("$name$", processName);
        m.setName(interpolated);
    }
    
    private void interpolateUserTaskTypes(ClassOrInterfaceType t, String inputClazzName, String outputClazzName) {
        SimpleName returnType = t.asClassOrInterfaceType().getName();
        interpolateUserTaskTypes(returnType, inputClazzName, outputClazzName);
        t.getTypeArguments().ifPresent(o -> interpolateUserTaskTypeArguments(o, inputClazzName, outputClazzName));
    }

    private void interpolateUserTaskTypes(SimpleName returnType, String inputClazzName, String outputClazzName) {
        String identifier = returnType.getIdentifier();
              
        returnType.setIdentifier(identifier.replace("$TaskInput$", inputClazzName));
        
        identifier = returnType.getIdentifier();
        returnType.setIdentifier(identifier.replace("$TaskOutput$", outputClazzName));
    }

    private void interpolateUserTaskTypeArguments(NodeList<Type> ta, String inputClazzName, String outputClazzName) {
        ta.stream().map(Type::asClassOrInterfaceType)
                .forEach(t -> interpolateUserTaskTypes(t, inputClazzName, outputClazzName));
    }

    public String generatedFilePath() {
        return relativePath;
    }
    
    protected boolean useInjection() {
        return this.annotator != null;
    }
    
    protected boolean isPublic() {
        return WorkflowProcess.PUBLIC_VISIBILITY.equalsIgnoreCase(process.getVisibility());
    }
    
}