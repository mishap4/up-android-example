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
package org.eclipse.uprotocol.example.client;

import static androidx.core.content.ContextCompat.getMainExecutor;

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkStatusOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.isOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.status;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.example.client.MainActivity.TAG;
import static org.eclipse.uprotocol.uri.factory.UriFactory.fromProto;

import static java.lang.System.currentTimeMillis;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.eclipse.uprotocol.client.usubscription.v3.InMemoryUSubscriptionClient;
import org.eclipse.uprotocol.client.usubscription.v3.SubscriptionChangeHandler;
import org.eclipse.uprotocol.client.usubscription.v3.USubscriptionClient;
import org.eclipse.uprotocol.client.utwin.v2.SimpleUTwinClient;
import org.eclipse.uprotocol.client.utwin.v2.UTwinClient;
import org.eclipse.uprotocol.common.util.UStatusUtils;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.communication.CallOptions;
import org.eclipse.uprotocol.communication.UClient;
import org.eclipse.uprotocol.communication.UPayload;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionResponse;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State;
import org.eclipse.uprotocol.example.v1.Door;
import org.eclipse.uprotocol.example.v1.DoorCommand;
import org.eclipse.uprotocol.example.v1.Example;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.transport.UTransport;
import org.eclipse.uprotocol.transport.UTransportAndroid;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;
import org.eclipse.uprotocol.v1.UUriBatch;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExampleFragment extends Fragment {
    private static final String EXAMPLE_SERVICE_PACKAGE = "org.eclipse.uprotocol.example.service";
    private static final ComponentName EXAMPLE_SERVICE_COMPONENT =
            new ComponentName(EXAMPLE_SERVICE_PACKAGE, EXAMPLE_SERVICE_PACKAGE + ".ExampleService");
    private static final int DOOR_ID = Example.TOPIC_DOORS_FRONT_LEFT.getResourceId();
    private final ServiceConnection mServiceConnectionListener = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mLog.i(TAG, join(Key.EVENT, "Service started", Key.PACKAGE, name.getPackageName()));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mLog.i(TAG, join(Key.EVENT, "Service stopped", Key.PACKAGE, name.getPackageName()));
        }
    };
    private final UListener mUListener = this::handleMessage;
    private final SubscriptionChangeHandler mSubscriptionChangeHandler = this::handleSubscriptionChange;
    private final Logger mLog = new Logger();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private CompletionStage<UStatus> mTransportStage;
    private UTransport mTransport;
    private UClient mClient;
    private USubscriptionClient mSubscriptionClient;
    private UTwinClient mUTwinClient;
    private Example.Stub mExampleStub;
    private TextView mDoorLockState;
    private Button mDoorLockButton;
    private Button mDoorUnlockButton;
    private Button mDoorUpdateButton;

    public static @NonNull ExampleFragment newInstance() {
        return new ExampleFragment();
    }

    private void startService() {
        try {
            final Intent intent = new Intent().setComponent(EXAMPLE_SERVICE_COMPONENT);
            requireContext().bindService(intent, mServiceConnectionListener, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            logStatus("bindService", toStatus(e), Key.PACKAGE, EXAMPLE_SERVICE_PACKAGE);
        }
    }

    private void stopService() {
        try {
            requireContext().unbindService(mServiceConnectionListener);
        } catch (Exception e) {
            logStatus("unbindService", toStatus(e), Key.PACKAGE, EXAMPLE_SERVICE_PACKAGE);
        }
    }

    @Override
    public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.example_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        startService();
        final Context context = requireContext();
        mTransport = UTransportAndroid.create(context, mExecutor);
        mTransportStage = mTransport.open()
                .thenApply(status -> {
                    checkStatusOk(logStatus("open", status));
                    mClient = UClient.create(mTransport);
                    mSubscriptionClient = new InMemoryUSubscriptionClient(mTransport, mClient, mClient);
                    mUTwinClient = new SimpleUTwinClient(mClient);
                    mExampleStub = Example.newStub(mClient);
                    return status;
                });
        mTransportStage
                .thenCompose(status -> subscribe(Example.TOPIC_DOORS_FRONT_LEFT))
                .thenRunAsync(() -> updateDoor(DOOR_ID), getMainExecutor(context));

        final View layout = requireView();
        mLog.setOutput(layout.findViewById(R.id.output), layout.findViewById(R.id.output_scroller));
        layout.findViewById(R.id.clear_output_button).setOnClickListener(bview -> mLog.clear());
        mDoorLockState = layout.findViewById(R.id.door_lock_state);
        mDoorLockButton = layout.findViewById(R.id.door_lock_button);
        mDoorLockButton.setOnClickListener(dview -> setDoorLocked(DOOR_ID, true));
        mDoorUnlockButton = layout.findViewById(R.id.door_unlock_button);
        mDoorUnlockButton.setOnClickListener(it -> setDoorLocked(DOOR_ID, false));
        mDoorUpdateButton = layout.findViewById(R.id.door_update_button);
        mDoorUpdateButton.setOnClickListener(it -> updateDoor(DOOR_ID));
        setButtonsEnabled(false);
        updateDoorState(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mLog.reset();
        stopService();

        mTransportStage
                .thenCompose(status -> unsubscribe(Example.TOPIC_DOORS_FRONT_LEFT))
                .whenComplete((status, exception) -> {
                    mTransport.close();
                    mExecutor.shutdown();
                    logStatus("close", STATUS_OK);
                });
    }

    @SuppressWarnings("SameParameterValue")
    private @NonNull CompletionStage<SubscriptionResponse> subscribe(@NonNull UUri topic) {
        return mSubscriptionClient.subscribe(topic, mUListener, CallOptions.DEFAULT, mSubscriptionChangeHandler)
                .whenComplete((response, exception) -> {
                    final UStatus status = (exception != null) ? toStatus(exception) : STATUS_OK;
                    final State state = (response != null) ? response.getStatus().getState() : State.UNSUBSCRIBED;
                    logStatus("subscribe", status, Key.TOPIC, stringify(topic), Key.STATE, state);
                });
    }

    @SuppressWarnings("SameParameterValue")
    private @NonNull CompletionStage<UStatus> unsubscribe(@NonNull UUri topic) {
        return mSubscriptionClient.unsubscribe(topic, mUListener)
                .exceptionally(UStatusUtils::toStatus)
                .thenApply(status -> logStatus("unsubscribe", status, Key.TOPIC, stringify(topic)));
    }

    private void handleMessage(@NonNull UMessage message) {
        final UUri source = message.getAttributes().getSource();
        if (Example.TOPIC_DOORS_FRONT_LEFT.equals(source)) {
            UPayload.unpack(message, Door.class).ifPresent(door ->
                    getMainExecutor(requireContext()).execute(() -> updateDoorState(door)));
        }
    }

    private void handleSubscriptionChange(@NonNull UUri topic, @NonNull SubscriptionStatus status) {
        mLog.i(TAG, join(Key.EVENT, "Subscription changed", Key.TOPIC, stringify(topic), Key.STATE, status.getState()));
    }

    private void updateDoorState(Door door) {
        if (door == null) {
            mDoorLockState.setText("");
        } else {
            mLog.i(TAG, join(Key.EVENT, "Door updated", Key.ID, door.getId(), "locked", door.getLocked()));
            mDoorLockState.setText(door.getLocked() ? R.string.locked : R.string.unlocked);
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        mDoorLockButton.setEnabled(enabled);
        mDoorUnlockButton.setEnabled(enabled);
        mDoorUpdateButton.setEnabled(enabled);
    }

    @SuppressWarnings("SameParameterValue")
    private void setDoorLocked(int id, boolean locked) {
        setButtonsEnabled(false);
        final DoorCommand.Action action = locked ? DoorCommand.Action.LOCK : DoorCommand.Action.UNLOCK;
        final DoorCommand request = DoorCommand.newBuilder()
                .setDoor(Door.newBuilder()
                        .setId(id)
                        .build())
                .setAction(action)
                .build();
        mLog.i(TAG, join(Key.REQUEST, "executeDoorCommand", Key.ID, id, Key.ACTION, action));
        final long before = currentTimeMillis();
        mExampleStub.executeDoorCommand(request)
                .handle((status, exception) -> {
                    final long delta = currentTimeMillis() - before;
                    if (exception != null) {
                        status = toStatus(exception);
                    }
                    logStatus("executeDoorCommand", status, Key.LATENCY, delta);
                    return null;
                })
                .thenRunAsync(() -> setButtonsEnabled(true), getMainExecutor(requireContext()));
    }

    @SuppressWarnings("SameParameterValue")
    private void updateDoor(int id) {
        setButtonsEnabled(false);
        final UUri topic = fromProto(Example.DESCRIPTOR, id);
        final UUriBatch topics = UUriBatch.newBuilder()
                .addUris(topic)
                .build();
        mLog.i(TAG, join(Key.REQUEST, "getLastMessages", Key.TOPIC, stringify(topic)));
        final long before = currentTimeMillis();
        mUTwinClient.getLastMessages(topics)
                .handle((response, exception) -> {
                    final long delta = currentTimeMillis() - before;
                    final UStatus status = (exception != null) ? toStatus(exception) : STATUS_OK;
                    logStatus("getLastMessages", status, Key.LATENCY, delta);
                    if (response != null) {
                        response.getResponsesList().forEach(messageResponse -> handleMessage(messageResponse.getMessage()));
                    }
                    return null;
                })
                .thenRunAsync(() -> setButtonsEnabled(true), getMainExecutor(requireContext()));
    }

    private @NonNull UStatus logStatus(@NonNull String method, @NonNull UStatus status, Object... args) {
        mLog.println(isOk(status) ? Log.INFO : Log.ERROR, TAG, status(method, status, args));
        return status;
    }
}
