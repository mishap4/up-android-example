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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

@SuppressWarnings("SwitchStatementWithTooFewBranches")
public class MainActivity extends AppCompatActivity {
    public static final String TAG = "example.client";
    private static final int TOTAL_TABS = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final TabLayout layout = findViewById(R.id.tabs);
        final ViewPager2 viewPager = findViewById(R.id.pager);
        viewPager.setAdapter(new MainPagerAdapter(getSupportFragmentManager(), getLifecycle(), TOTAL_TABS));
        if (TOTAL_TABS <= 1) {
            layout.setVisibility(TabLayout.GONE);
        }

        new TabLayoutMediator(layout, viewPager, (tab, position) -> tab.setText(switch (position) {
            default -> R.string.example_label;
        })).attach();
    }

    private static class MainPagerAdapter extends FragmentStateAdapter {
        private final int mTotalTabs;

        public MainPagerAdapter(FragmentManager manager, Lifecycle lifecycle, int totalTabs) {
            super(manager, lifecycle);
            mTotalTabs = totalTabs;
        }

        @Override
        public @NonNull Fragment createFragment(int position) {
            return switch (position) {
                default -> ExampleFragment.newInstance();
            };
        }

        @Override
        public int getItemCount() {
            return mTotalTabs;
        }
    }
}
