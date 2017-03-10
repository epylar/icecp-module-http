/*
 * Copyright (c) 2017 Intel Corporation
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

package com.intel.icecp.module.httpbridge.message;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.intel.icecp.core.Module;

public interface OnCommandMessage<M extends Module> {
    /**
     * Default implementation of executing the onCommandMessage in a pool To run this synchronously in a pool, call {@code poolCommandMessage (...).get();}
     * 
     * @param executorServive Executor service
     * @param context Contect passed tothe executor service
     * @return Future to test for completion.
     */
    public default Future<?> poolCommandMessage(ExecutorService executorServive, M context) {
        // WrapperTask wrapperTask = new WrapperTask(new Runnable(), connectionId);
        return executorServive.submit(new Runnable() {
            @Override
            public void run() {
                onCommandMessage(context);
            }
        });
    }

    public void onCommandMessage(M context);

    public default String onValidate(M context) {
        return null;
    }
}
