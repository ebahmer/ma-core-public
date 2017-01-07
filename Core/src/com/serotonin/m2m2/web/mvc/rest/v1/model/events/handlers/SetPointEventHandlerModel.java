/**
 * Copyright (C) 2017 Infinite Automation Software. All rights reserved.
 *
 */
package com.serotonin.m2m2.web.mvc.rest.v1.model.events.handlers;

import com.serotonin.m2m2.vo.event.SetPointEventHandlerVO;

/**
 * 
 * @author Terry Packer
 */
public class SetPointEventHandlerModel extends AbstractEventHandlerModel<SetPointEventHandlerVO>{

	/**
	 * @param data
	 */
	public SetPointEventHandlerModel(SetPointEventHandlerVO data) {
		super(data);
	}
	
	public SetPointEventHandlerModel(){
		super(new SetPointEventHandlerVO());
	}
	
    public int getTargetPointId() {
        return this.data.getTargetPointId();
    }

    public void setTargetPointId(int targetPointId) {
        this.data.setTargetPointId(targetPointId);
    }

    public int getActiveAction() {
        return this.data.getActiveAction();
    }

    public void setActiveAction(int activeAction) {
        this.data.setActiveAction(activeAction);
    }

    public int getInactiveAction() {
        return this.data.getInactiveAction();
    }

    public void setInactiveAction(int inactiveAction) {
        this.data.setInactiveAction(inactiveAction);
    }
    
    public String getActiveValueToSet() {
        return this.data.getActiveValueToSet();
    }

    public void setActiveValueToSet(String activeValueToSet) {
        this.data.setActiveValueToSet(activeValueToSet);
    }

    public int getActivePointId() {
        return this.data.getActivePointId();
    }

    public void setActivePointId(int activePointId) {
        this.data.setActivePointId(activePointId);
    }

    public String getInactiveValueToSet() {
        return this.data.getInactiveValueToSet();
    }

    public void setInactiveValueToSet(String inactiveValueToSet) {
        this.data.setInactiveValueToSet(inactiveValueToSet);
    }

    public int getInactivePointId() {
        return this.data.getInactivePointId();
    }

    public void setInactivePointId(int inactivePointId) {
        this.data.setInactivePointId(inactivePointId);
    }
}