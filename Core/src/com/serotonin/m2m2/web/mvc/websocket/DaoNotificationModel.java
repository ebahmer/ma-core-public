/**
 * Copyright (C) 2015 Infinite Automation Systems. All rights reserved.
 * http://infiniteautomation.com/
 */
package com.serotonin.m2m2.web.mvc.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Jared Wiltshire
 */
public class DaoNotificationModel {
    /**
     * add, update or delete
     */
    @JsonProperty
    String action;
    
    /**
     * The vo object
     */
    @JsonProperty
    Object object;
    
    /**
     * A random string which identifies the initiator of the notification,
     * may be null if none was given. Used to filter out events which the initiator
     * of the WebSocket already knows about 
     */
    @JsonProperty
    String initiatorId;
    
    public DaoNotificationModel() {
    }
    
    public DaoNotificationModel(String action, Object object, String initiatorId) {
        this.action = action;
        this.object = object;
        this.initiatorId = initiatorId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object object) {
        this.object = object;
    }

    public String getInitiatorId() {
        return initiatorId;
    }

    public void setInitiatorId(String initiatorId) {
        this.initiatorId = initiatorId;
    }
}
