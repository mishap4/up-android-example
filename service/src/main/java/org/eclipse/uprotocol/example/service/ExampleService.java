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
package org.eclipse.uprotocol.example.service;

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkStatusOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.isOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.status;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;

import static java.util.concurrent.CompletableFuture.allOf;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.communication.RequestHandler;
import org.eclipse.uprotocol.communication.UClient;
import org.eclipse.uprotocol.communication.UPayload;
import org.eclipse.uprotocol.communication.UStatusException;
import org.eclipse.uprotocol.example.v1.Door;
import org.eclipse.uprotocol.example.v1.DoorCommand;
import org.eclipse.uprotocol.example.v1.Example;
import org.eclipse.uprotocol.transport.UTransport;
import org.eclipse.uprotocol.transport.UTransportAndroid;
import org.eclipse.uprotocol.uri.factory.UriFactory;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExampleService extends Service {
    private static final String TAG = Example.NAME;
    private static final Set<Integer> DOOR_IDS = Set.of(
            Example.TOPIC_DOORS_FRONT_LEFT.getResourceId(),
            Example.TOPIC_DOORS_FRONT_RIGHT.getResourceId());

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Map<UUri, RequestHandler> mMethodHandlers = Map.of(
            Example.METHOD_EXECUTE_DOOR_COMMAND, this::executeDoorCommand);
    private CompletionStage<UStatus> mTransportStage;
    private UTransport mTransport;
    private UClient mClient;

    @Override
    public void onCreate() {
        super.onCreate();
        mTransport = UTransportAndroid.create(getApplicationContext(), Example.SERVICE, mExecutor);
        mTransportStage = mTransport.open()
                .thenApply(status -> {
                    checkStatusOk(logStatus("open", status));
                    mClient = UClient.create(mTransport);
                    return status;
                });
        mTransportStage
                .thenCompose(status -> allOf(mMethodHandlers.entrySet().stream()
                        .map(it -> registerRequestHandler(it.getKey(), it.getValue()))
                        .toArray(CompletableFuture[]::new)));
    }

    @Override
    public @Nullable IBinder onBind(@NonNull Intent intent) {
        return new Binder();
    }

    @Override
    public void onDestroy() {
        mTransportStage
                .thenCompose(status -> allOf(mMethodHandlers.entrySet().stream()
                        .map(it -> unregisterRequestHandler(it.getKey(), it.getValue()))
                        .toArray(CompletableFuture[]::new)))
                .whenComplete((status, exception) -> {
                    mTransport.close();
                    mExecutor.shutdown();
                    logStatus("close", STATUS_OK);
                });
        super.onDestroy();
    }

    private @NonNull CompletableFuture<UStatus> registerRequestHandler(@NonNull UUri methodUri, @NonNull RequestHandler handler) {
        return mClient.registerRequestHandler(methodUri, handler)
                .thenApply(it -> logStatus("registerRequestHandler", it, Key.URI, stringify(methodUri)))
                .toCompletableFuture();
    }

    private @NonNull CompletableFuture<UStatus> unregisterRequestHandler(@NonNull UUri methodUri, @NonNull RequestHandler handler) {
        return mClient.unregisterRequestHandler(methodUri, handler)
                .thenApply(it -> logStatus("unregisterRequestHandler", it, Key.URI, stringify(methodUri)))
                .toCompletableFuture();
    }

    private @NonNull UPayload executeDoorCommand(@NonNull UMessage message) {
        UStatus status;
        try {
            final DoorCommand request = UPayload.unpack(message, DoorCommand.class)
                    .orElseThrow(IllegalArgumentException::new);
            final int id = request.getDoor().getId();
            final DoorCommand.Action action = request.getAction();
            checkArgument(DOOR_IDS.contains(id), "Unknown door: " + id);
            Log.i(TAG, join(Key.REQUEST, "executeDoorCommand", Key.ID, id, Key.ACTION, action));
            final boolean locked = switch (action) {
                case LOCK -> true;
                case UNLOCK -> false;
                default -> throw new UStatusException(UCode.INVALID_ARGUMENT, "Unknown action: " + action);
            };
            mClient.publish(UriFactory.fromProto(Example.DESCRIPTOR, id), UPayload.packToAny(Door.newBuilder()
                    .setId(id)
                    .setLocked(locked)
                    .build()));
            status = STATUS_OK;
        } catch (Exception e) {
            status = toStatus(e);
        }
        logStatus("executeDoorCommand", status);
        return UPayload.packToAny(status);
    }

    private @NonNull UStatus logStatus(@NonNull String method, @NonNull UStatus status, Object... args) {
        Log.println(isOk(status) ? Log.INFO : Log.ERROR, TAG, status(method, status, args));
        return status;
    }
}
