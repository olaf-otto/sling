/*
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
 */
package org.apache.sling.replication.trigger.impl;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import java.util.Map;
import java.util.Set;

import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.replication.communication.ReplicationActionType;
import org.apache.sling.replication.communication.ReplicationRequest;
import org.apache.sling.replication.component.ManagedReplicationComponent;
import org.apache.sling.replication.trigger.ReplicationTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link org.apache.sling.replication.trigger.ReplicationTrigger} that listens for certain events and persists them
 * under a specific path in the repo
 */
public class PersistingJcrEventReplicationTrigger extends AbstractJcrEventTrigger implements ReplicationTrigger, ManagedReplicationComponent {

    private static final String DEFAULT_NUGGETS_PATH = "/var/nuggets";

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final String nuggetsPath;

    public PersistingJcrEventReplicationTrigger(SlingRepository repository, String path, String servicename, String nuggetsPath) {
        super(repository, path, servicename);
        this.nuggetsPath = nuggetsPath == null || nuggetsPath.length() == 0 ? DEFAULT_NUGGETS_PATH : nuggetsPath;
    }

    @Override
    protected ReplicationRequest processEvent(Event event) throws RepositoryException {
        log.info("processing event {}", event);

        ReplicationRequest replicationRequest = null;

        Session session = getSession();

        if (!session.nodeExists(nuggetsPath)) {
            initializeNuggetsPath(session);
        }

        if (session.hasPermission(nuggetsPath, Session.ACTION_ADD_NODE)) {
            log.info("persisting event under {}", nuggetsPath);
            Node nuggetsNode = session.getNode(nuggetsPath);
            if (nuggetsNode != null) {
                String nodeName = event.getIdentifier() != null ? event.getIdentifier() : String.valueOf(System.nanoTime());
                Node createdNode = nuggetsNode.addNode(nodeName);
                if (createdNode != null) {
                    String path = createdNode.getPath();
                    nuggetsNode.setProperty("path", event.getPath());
                    nuggetsNode.setProperty("date", event.getDate());
                    nuggetsNode.setProperty("type", event.getType());
                    nuggetsNode.setProperty("userData", event.getUserData());
                    nuggetsNode.setProperty("userID", event.getUserID());

                    Set<Map.Entry> set = event.getInfo().entrySet();
                    for (Map.Entry entry : set) {
                        nuggetsNode.setProperty("info." + entry.getKey(), String.valueOf(entry.getValue()));
                    }
                    session.save();
                    log.info("event persisted at {}", path);
                    replicationRequest = new ReplicationRequest(System.currentTimeMillis(), ReplicationActionType.ADD, path);
                } else {
                    log.warn("could not create node {}", nuggetsPath + "/" + nodeName);
                }
            } else {
                log.warn("could not get node {} to persist event", nuggetsPath);
            }
        } else {
            log.warn("not enough privileges to persist the event {} under {}", event, nuggetsPath);
        }

        return replicationRequest;
    }

    private void initializeNuggetsPath(Session session) throws RepositoryException {
        log.info("initializing nuggets path");
        if (session != null) {
            Node parent = session.getRootNode();
            if (session.hasPermission(parent.getPath(), Session.ACTION_ADD_NODE)) {
                for (String nodeName : nuggetsPath.split("/")) {
                    if (nodeName.length() > 0) {
                        if (!parent.hasNode(nodeName)) {
                            log.info("adding {}", nodeName);
                            parent = parent.addNode(nodeName, "sling:Folder");
                        } else {
                            log.info("{} exists", nodeName);
                            parent = parent.getNode(nodeName);
                        }
                    }
                }
                session.save();
            }
        }
    }

    public void enable() {
        log.info("enabling persisting jcr event listener");
        Session session = null;
        try {
            session = getSession();
            if (!session.nodeExists(nuggetsPath)) {
                initializeNuggetsPath(session);
            }
        } catch (RepositoryException e) {
            log.warn("could not create nuggets path " + nuggetsPath, e);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    public void disable() {
        log.info("disabling persisting jcr event listener");
    }
}