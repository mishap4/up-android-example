/*
 * Copyright (c) 2023 General Motors GTO LLC
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * SPDX-FileType: SOURCE
 * SPDX-FileCopyrightText: 2023 General Motors GTO LLC
 * SPDX-License-Identifier: Apache-2.0
 */
package org.eclipse.uprotocol.example.v1;

import static org.eclipse.uprotocol.communication.RpcMapper.mapResponse;
import static org.eclipse.uprotocol.communication.UPayload.packToAny;
import static org.eclipse.uprotocol.uri.validator.UriValidator.DEFAULT_RESOURCE_ID;

import com.google.protobuf.Descriptors.ServiceDescriptor;

import org.eclipse.uprotocol.Uoptions;
import org.eclipse.uprotocol.communication.CallOptions;
import org.eclipse.uprotocol.communication.RpcClient;
import org.eclipse.uprotocol.uri.factory.UriFactory;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

@SuppressWarnings({"unused", "SameParameterValue"})
public class Example {
    public static final ServiceDescriptor DESCRIPTOR = ExampleProto.getDescriptor().getServices().get(0);
    public static final String NAME = DESCRIPTOR.getOptions().getExtension(Uoptions.serviceName);
    public static final UUri SERVICE = UriFactory.fromProto(DESCRIPTOR, DEFAULT_RESOURCE_ID);
    public static final UUri METHOD_EXECUTE_DOOR_COMMAND = UriFactory.fromProto(DESCRIPTOR, 1);
    public static final UUri TOPIC_DOORS_FRONT_LEFT = UriFactory.fromProto(DESCRIPTOR, 0x8000);
    public static final UUri TOPIC_DOORS_FRONT_RIGHT = UriFactory.fromProto(DESCRIPTOR, 0x8001);

    private Example() {}

    public static Example.Stub newStub(RpcClient proxy) {
        return newStub(proxy, null, CallOptions.DEFAULT);
    }

    public static Example.Stub newStub(RpcClient proxy, CallOptions options) {
        return newStub(proxy, null, options);
    }

    public static Example.Stub newStub(RpcClient proxy, String authority, CallOptions options) {
        return new Example.Stub(proxy, authority, options);
    }

    public static class Stub {
        private final RpcClient proxy;
        private final String authority;
        private final CallOptions options;

        private Stub(RpcClient proxy, String authority, CallOptions options) {
            this.proxy = proxy;
            this.authority = authority;
            this.options = options;
        }

        private UUri appendAuthority(UUri methodUri) {
            return (authority != null) ? UUri.newBuilder(methodUri).setAuthorityName(authority).build() : methodUri;
        }

        public Optional<String> getAuthority() {
            return (authority != null) ? Optional.of(authority) : Optional.empty();
        }

        public CallOptions getOptions() {
            return options;
        }

        public CompletionStage<UStatus> executeDoorCommand(DoorCommand request) {
            return mapResponse(proxy.invokeMethod(appendAuthority(METHOD_EXECUTE_DOOR_COMMAND), packToAny(request), options), UStatus.class);
        }
    }
}
