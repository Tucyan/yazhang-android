package com.example.yazhang;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.example.yazhang.data.AppDatabase;
import com.example.yazhang.data.LedgerRepository;
import com.example.yazhang.ui.AgentView;
import com.example.yazhang.ui.EntryDialog;
import com.example.yazhang.ui.HomeView;
import com.example.yazhang.ui.ImageEntryFlow;
import com.example.yazhang.ui.RefreshablePage;

public class MainActivity extends LedgerActivity {

    static final String STATE_PAGE = "current_page";

    private LedgerRepository repository;
    private HomeView home;
    private AgentView agent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppDatabase database = AppDatabase.get(this);
        repository = new LedgerRepository(database);
        ImageEntryFlow imageEntryFlow = new ImageEntryFlow(this, repository, this::refreshPages);
        home = new HomeView(this, repository, this::openManualEntry, imageEntryFlow::start);
        agent = new AgentView(this, repository, database.agentMessageDao());
        int requestedPage = savedInstanceState == null
                ? getIntent().getIntExtra(STATE_PAGE, 0) : savedInstanceState.getInt(STATE_PAGE, 0);
        int page = requestedPage == 2 ? 2 : 0;
        showLayout(page, getPage(page).getView());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showPage(intent.getIntExtra(STATE_PAGE, 0));
    }

    @Override
    protected void onResume() {
        super.onResume();
        getPage(currentPage).refresh();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_PAGE, currentPage);
    }

    public RefreshablePage getPage(int index) {
        return index == 2 ? agent : home;
    }

    @Override
    public void showPage(int index) {
        if (index == 1 || index == 3) {
            super.showPage(index);
            return;
        }
        int page = index == 2 ? 2 : 0;
        if (page != currentPage) {
            displayPage(page, getPage(page).getView());
        }
        getPage(page).refresh();
    }

    private void openManualEntry(String initialType) {
        EntryDialog.show(this, repository, initialType, this::refreshPages);
    }

    private void refreshPages() {
        home.refresh();
        agent.refresh();
    }
}
