/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.compiler.canonical;

import static com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType;

import java.util.Map;
import java.util.Map.Entry;

import org.drools.core.util.StringUtils;
import org.jbpm.process.core.Work;
import org.jbpm.process.core.context.variable.Mappable;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.kie.api.definition.process.Node;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

public abstract class AbstractVisitor {

    protected static final String FACTORY_FIELD_NAME = "factory";
    protected static final String KCONTEXT_VAR = "kcontext";

    public void visitNode(Node node, BlockStmt body, VariableScope variableScope, ProcessMetaData metadata) {
        visitNode(FACTORY_FIELD_NAME, node, body, variableScope, metadata);
    }

    public void visitNode(String factoryField, Node node, BlockStmt body, VariableScope variableScope, ProcessMetaData metadata) {

    }

    protected MethodCallExpr addFactoryMethodWithArgs(String factoryField, BlockStmt body, String methodName, Expression... args) {

        return addFactoryMethodWithArgs(body, factoryField, methodName, args);
    }

    protected MethodCallExpr addFactoryMethodWithArgs(BlockStmt body, String object, String methodName, Expression... args) {
        MethodCallExpr variableMethod = new MethodCallExpr(new NameExpr(object), methodName);

        for (Expression arg : args) {
            variableMethod.addArgument(arg);
        }
        body.addStatement(variableMethod);

        return variableMethod;
    }

    protected MethodCallExpr addFactoryMethodWithArgsWithAssignment(String factoryField, BlockStmt body, Class<?> typeClass, String variableName, String methodName, Expression... args) {
        ClassOrInterfaceType type = new ClassOrInterfaceType(null, typeClass.getCanonicalName());

        MethodCallExpr variableMethod = new MethodCallExpr(new NameExpr(factoryField), methodName);

        for (Expression arg : args) {
            variableMethod.addArgument(arg);
        }

        AssignExpr assignExpr = new AssignExpr(
                                               new VariableDeclarationExpr(type, variableName),
                                               variableMethod,
                                               AssignExpr.Operator.ASSIGN);
        body.addStatement(assignExpr);

        return variableMethod;
    }

    public static Statement makeAssignment(Variable v) {
        String name = v.getName();
        return makeAssignment(name, v);
    }

    public static Statement makeAssignment(String targetLocalVariable, Variable processVariable) {
        ClassOrInterfaceType type = parseClassOrInterfaceType(processVariable.getType().getStringType());
        // `type` `name` = (`type`) `kcontext.getVariable
        AssignExpr assignExpr = new AssignExpr(
                                               new VariableDeclarationExpr(type, targetLocalVariable),
                                               new CastExpr(
                                                            type,
                                                            new MethodCallExpr(
                                                                               new NameExpr(KCONTEXT_VAR),
                                                                               "getVariable")
                                                                                             .addArgument(new StringLiteralExpr(targetLocalVariable))),
                                               AssignExpr.Operator.ASSIGN);

        return new ExpressionStmt(assignExpr);
    }

    protected Statement makeAssignmentFromModel(Variable v) {

        return makeAssignmentFromModel(v, v.getName());
    }

    protected Statement makeAssignmentFromModel(Variable v, String name) {
        ClassOrInterfaceType type = parseClassOrInterfaceType(v.getType().getStringType());


        // `type` `name` = (`type`) `model.get<Name>
        AssignExpr assignExpr = new AssignExpr(
                                               new VariableDeclarationExpr(type, name),
                                               new CastExpr(
                                                            type,
                                                            new MethodCallExpr(
                                                                               new NameExpr("model"),
                                                                               "get" + StringUtils.capitalize(name))),
                                               AssignExpr.Operator.ASSIGN);

        return new ExpressionStmt(assignExpr);
    }

    protected String getOrDefault(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        return value;
    }



    protected void addWorkItemParameters(Work work, BlockStmt body, String variableName) {

        for (Entry<String, Object> entry : work.getParameters().entrySet()) {
            if (entry.getValue() == null) {
                continue; // interfaceImplementationRef ?
            }
            addFactoryMethodWithArgs(body, variableName, "workParameter", new StringLiteralExpr(entry.getKey()), new StringLiteralExpr(entry.getValue().toString()));
        }
    }

    protected void addNodeMappings(Mappable node, BlockStmt body, String variableName) {
        for (Entry<String, String> entry : node.getInMappings().entrySet()) {
            addFactoryMethodWithArgs(body, variableName, "inMapping", new StringLiteralExpr(entry.getKey()), new StringLiteralExpr(entry.getValue()));
        }
        for (Entry<String, String> entry : node.getOutMappings().entrySet()) {
            addFactoryMethodWithArgs(body, variableName, "outMapping", new StringLiteralExpr(entry.getKey()), new StringLiteralExpr(entry.getValue()));
        }
    }

    protected void visitMetaData(Map<String, Object> metadata, BlockStmt body, String variableName) {
        for (Entry<String, Object> entry : metadata.entrySet()) {
            Expression value = null;

            if (entry.getValue() instanceof Boolean) {
                value = new BooleanLiteralExpr((Boolean) entry.getValue());
            } else if (entry.getValue() instanceof Integer) {
                value = new IntegerLiteralExpr((Integer) entry.getValue());
            } else if (entry.getValue() instanceof Long) {
                value = new LongLiteralExpr((Long) entry.getValue());
            } else if (entry.getValue() instanceof String) {
                value = new StringLiteralExpr(entry.getValue().toString());
            }
            if (value != null) {
                addFactoryMethodWithArgs(body, variableName, "metaData", new StringLiteralExpr(entry.getKey()), value);
            }
        }
    }

    protected String extractVariableFromExpression(String variableExpression) {
        if (variableExpression.startsWith("#{")) {
            return variableExpression.substring(2, variableExpression.indexOf("."));
        }
        return variableExpression;
    }
}
