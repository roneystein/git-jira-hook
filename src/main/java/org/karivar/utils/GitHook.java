/**
 * Copyright (C) 2015 Per Ivar Gjerløw
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */
package org.karivar.utils;

import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.karivar.utils.domain.IssueKeyNotFoundException;
import org.karivar.utils.domain.JiraIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class GitHook {
    private final Logger logger = LoggerFactory.getLogger(GitHook.class);
    private static GitHook githook;
    private ResourceBundle messages;
    private static CommitMessageManipulator manipulator;
    private JiraConnector jiraConnector;
    private JiraIssue populatedIssue;
    private Multimap<String, List<String>> issueTypesAndStatuses;
    private List<String> issueLinks;
    private Optional<String> jiraProjectKeys;

    public static void main(String[] args) {
        githook = new GitHook();
        manipulator = new CommitMessageManipulator();
        githook.init(args);
    }

    private void init(String[] args) {

        loadI18nMessages(GitConfig.getLanguageSettings());
        printInitalText();

        if (args != null && args.length > 0) {
            manipulator.loadCommitMessage(args[0]);
            loadApplicationProperties();
            fetchPopulatedJiraIssue();
            checkStateAndManipulateCommitMessage();
            updateCommitMessage();

        } else {
            logger.error(messages.getString("error.githook.nocommitfile"));
        }
    }

    private void updateCommitMessage() {
    }

    private void checkStateAndManipulateCommitMessage() {
    }

    private void fetchPopulatedJiraIssue() {
        // Get options for
        //   1: override communication with JIRA altogether
        //   2: override (e.g force) commits
        boolean isJiraCommunicationOverridden = manipulator.isCommunicationOverridden();
        boolean isCommitOverridden = manipulator.isCommitOverridden();

        if (!isJiraCommunicationOverridden && !isCommitOverridden) {
            // Contact JIRA, fetch JIRA issue and check state and return populated issue
            logger.debug("Preparing to communicate with Jira");

            jiraConnector = new JiraConnector();
            jiraConnector.connectToJira(GitConfig.getJiraUsername(),
                    GitConfig.getJiraEncodedPassword(), GitConfig.getJiraAddress());

            try {
                Optional<String> issueKey = manipulator.getJiraIssueKeyFromCommitMessage(
                        getJiraIssueKey(jiraProjectKeys));
                populatedIssue = jiraConnector.getJiraPopulatedIssue(issueKey, issueLinks);
            } catch (IssueKeyNotFoundException e) {
                logger.error(e.getLocalizedMessage());
            }
        } else {
            logger.debug("Communication with Jira is overridden or commit is overridden");
        }
    }

    private void loadApplicationProperties() {
        // Load JIRA project list
        jiraProjectKeys =  GitConfig.getJiraProjects();

        // Load JIRA issue types and their accepted statuses.
        issueTypesAndStatuses = loadIssueTypesAndStatuses();

        // Load JIRA issue links
        issueLinks = loadIssueLinks();

    }

    private void loadI18nMessages(Optional<String> languageSettings) {
        messages = languageSettings.map(s -> ResourceBundle.getBundle("messages",
                Locale.forLanguageTag(s))).orElseGet(() -> ResourceBundle.getBundle("messages"));
    }

    private Multimap<String, List<String>> loadIssueTypesAndStatuses() {
        Multimap<String, List<String>> issueTypesAndStatuses = ArrayListMultimap.create();

        Properties properties = loadPropertiesFile("issuetypes.properties");

        if (!properties.isEmpty()) {
            Iterator<Object> propertiesKeysIterator = properties.keySet().iterator();
            while (propertiesKeysIterator.hasNext()) {
                String key = (String)propertiesKeysIterator.next();

                String values = properties.getProperty(key);

                if (key.contains("_")) {
                    key = key.replaceAll("_", " ");
                }

                List<String> items = Lists.newArrayList(Splitter.on(", ").split(values));
                issueTypesAndStatuses.put(key, items);
            }
        }


        return issueTypesAndStatuses;
    }

    private List<String> loadIssueLinks() {
        List<String> links = Lists.newArrayList();

        Properties properties = loadPropertiesFile("issuelinks.properties");

        if (!properties.isEmpty()) {
            String values = properties.getProperty("issuelinks");
            links = Lists.newArrayList(Splitter.on(", ").split(values));
        }

        return links;
    }

    private Properties loadPropertiesFile(String filename) {
        Properties properties = new Properties();

        try {
            InputStream reader = getClass().getClassLoader().getResourceAsStream(filename);
            properties.load(reader);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        return properties;
    }

    private void printInitalText() {
        logger.info(messages.getString("startup.information") +  " 0.0.1");
    }

    private String getJiraIssueKey(Optional<String> jiraProjectPattern) {
        String issueKey = null;

        if (jiraProjectPattern.isPresent()) {

            Optional<String> possibleIssueKey = manipulator.getJiraIssueKeyFromCommitMessage(
                    jiraProjectPattern.get());

            if (possibleIssueKey.isPresent()) {
                issueKey = possibleIssueKey.get();
            }

        } else {
            logger.debug("There are no project keys registered in git config");
        }

        return issueKey;
    }


}
