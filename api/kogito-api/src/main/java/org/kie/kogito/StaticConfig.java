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

package org.kie.kogito;

import org.kie.kogito.process.ProcessConfig;
import org.kie.kogito.rules.RuleConfig;

public class StaticConfig implements Config {

    private final ProcessConfig processConfig;
    private final RuleConfig ruleConfig;

    public StaticConfig(ProcessConfig processConfig, RuleConfig ruleConfig) {
        this.processConfig = processConfig;
        this.ruleConfig = ruleConfig;
    }

    @Override
    public ProcessConfig process() {
        return this.processConfig;
    }

    @Override
    public RuleConfig rule() {
        return this.ruleConfig;
    }

    @Override
    public Addons addons() {
        return Addons.EMTPY;
    }
}
