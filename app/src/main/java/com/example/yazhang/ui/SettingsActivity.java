package com.example.yazhang.ui;

import android.os.Bundle;

import com.example.yazhang.LedgerActivity;
import com.example.yazhang.data.AppDatabase;
import com.example.yazhang.data.LedgerRepository;

public final class SettingsActivity extends LedgerActivity {

    private SettingsView page;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LedgerRepository repository = new LedgerRepository(AppDatabase.get(this));
        page = new SettingsView(this, repository, this::refresh);
        showLayout(3, page.getView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        page.refresh();
    }

    SettingsView settingsView() {
        return page;
    }

    private void refresh() {
        page.refresh();
    }
}
