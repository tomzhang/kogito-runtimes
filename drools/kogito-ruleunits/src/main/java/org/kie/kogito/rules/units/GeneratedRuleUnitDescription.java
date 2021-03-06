/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.kogito.rules.units;

import java.util.function.Function;

import org.drools.core.addon.TypeResolver;
import org.kie.kogito.rules.RuleUnitConfig;

public class GeneratedRuleUnitDescription extends AbstractRuleUnitDescription {

    private final Function<String, Class<?>> typeResolver;
    private final String name;
    private final String packageName;
    private final String simpleName;

    public GeneratedRuleUnitDescription(String name, Function<String, Class<?>> typeResolver) {
        this.typeResolver = typeResolver;
        this.name = name;
        this.simpleName = name.substring(name.lastIndexOf('.') + 1);
        this.packageName = name.substring(0, name.lastIndexOf('.'));
        setConfig(RuleUnitConfig.Default);
    }

    public GeneratedRuleUnitDescription(String name, TypeResolver typeResolver) {
        this(name, fqcn -> uncheckedLoadClass(typeResolver, fqcn));
    }

    public GeneratedRuleUnitDescription(String name, ClassLoader contextClassLoader) {
        this(name, fqcn -> uncheckedLoadClass(contextClassLoader, fqcn));
    }

    @Override
    @Deprecated
    public Class<?> getRuleUnitClass() {
        return null;
    }

    @Override
    public String getCanonicalName() {
        return getPackageName() + '.' + getSimpleName();
    }

    @Override
    public String getSimpleName() {
        return simpleName;
    }

    @Override
    public String getPackageName() {
        return packageName;
    }

    @Override
    public String getRuleUnitName() {
        return name;
    }

    public void putSimpleVar(String name, String varTypeFQCN) {
        Class<?> varType = typeResolver.apply(varTypeFQCN);
        putSimpleVar(name, varType);
    }

    public void putDatasourceVar(String name, String datasourceTypeFQCN, String datasourceParameterTypeFQCN) {
        putDatasourceVar(
                name,
                typeResolver.apply(datasourceTypeFQCN),
                typeResolver.apply(datasourceParameterTypeFQCN));
    }

    public void putSimpleVar(String name, Class<?> varType) {
        putRuleUnitVariable(new SimpleRuleUnitVariable(name, varType));
    }

    public void putDatasourceVar(String name, Class<?> datasourceType, Class<?> datasourceParameterType) {
        putRuleUnitVariable(new SimpleRuleUnitVariable(name, datasourceType, datasourceParameterType, true));
    }

    private static Class<?> uncheckedLoadClass(TypeResolver typeResolver, String fqcn) {
        try {
            return typeResolver.resolveType(fqcn);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static Class<?> uncheckedLoadClass(ClassLoader classLoader, String fqcn) {
        try {
            return classLoader.loadClass(fqcn);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
