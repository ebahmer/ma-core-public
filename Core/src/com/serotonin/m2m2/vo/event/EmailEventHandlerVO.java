/**
 * Copyright (C) 2016 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.vo.event;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.serotonin.db.pair.IntStringPair;
import com.serotonin.json.JsonException;
import com.serotonin.json.JsonReader;
import com.serotonin.json.ObjectWriter;
import com.serotonin.json.type.JsonArray;
import com.serotonin.json.type.JsonObject;
import com.serotonin.json.type.JsonValue;
import com.serotonin.json.util.TypeDefinition;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.Common.TimePeriods;
import com.serotonin.m2m2.db.dao.DataPointDao;
import com.serotonin.m2m2.i18n.ProcessResult;
import com.serotonin.m2m2.i18n.TranslatableJsonException;
import com.serotonin.m2m2.rt.event.handlers.EmailHandlerRT;
import com.serotonin.m2m2.rt.event.handlers.EventHandlerRT;
import com.serotonin.m2m2.util.ExportCodes;
import com.serotonin.m2m2.util.VarNames;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.web.dwr.beans.RecipientListEntryBean;
import com.serotonin.m2m2.web.mvc.rest.v1.model.events.handlers.AbstractEventHandlerModel;
import com.serotonin.m2m2.web.mvc.rest.v1.model.events.handlers.EmailEventHandlerModel;
import com.serotonin.util.SerializationHelper;

/**
 * @author Terry Packer
 *
 */
public class EmailEventHandlerVO extends AbstractEventHandlerVO<EmailEventHandlerVO>{

    public static final int RECIPIENT_TYPE_ACTIVE = 1;
    public static final int RECIPIENT_TYPE_ESCALATION = 2;
    public static final int RECIPIENT_TYPE_INACTIVE = 3;

    public static ExportCodes RECIPIENT_TYPE_CODES = new ExportCodes();
    static {
        RECIPIENT_TYPE_CODES.addElement(RECIPIENT_TYPE_ACTIVE, "ACTIVE", "eventHandlers.recipientType.active");
        RECIPIENT_TYPE_CODES.addElement(RECIPIENT_TYPE_ESCALATION, "ESCALATION",
                "eventHandlers.recipientType.escalation");
        RECIPIENT_TYPE_CODES.addElement(RECIPIENT_TYPE_INACTIVE, "INACTIVE", "eventHandlers.recipientType.inactive");
    }

	
	private List<RecipientListEntryBean> activeRecipients;
    private boolean sendEscalation;
    private int escalationDelayType = TimePeriods.HOURS;
    private int escalationDelay = 1;
    private List<RecipientListEntryBean> escalationRecipients;
    private boolean sendInactive;
    private boolean inactiveOverride;
    private List<RecipientListEntryBean> inactiveRecipients;
    private boolean includeSystemInfo; //Include Work Items and Service Thread Pool Data
    private int includePointValueCount = 10;
    private boolean includeLogfile;
    private String customTemplate;
    private List<IntStringPair> additionalContext = new ArrayList<IntStringPair>();
    
    public List<RecipientListEntryBean> getActiveRecipients() {
        return activeRecipients;
    }

    public void setActiveRecipients(List<RecipientListEntryBean> activeRecipients) {
        this.activeRecipients = activeRecipients;
    }

    public int getEscalationDelay() {
        return escalationDelay;
    }

    public void setEscalationDelay(int escalationDelay) {
        this.escalationDelay = escalationDelay;
    }

    public int getEscalationDelayType() {
        return escalationDelayType;
    }

    public void setEscalationDelayType(int escalationDelayType) {
        this.escalationDelayType = escalationDelayType;
    }

    public List<RecipientListEntryBean> getEscalationRecipients() {
        return escalationRecipients;
    }

    public void setEscalationRecipients(List<RecipientListEntryBean> escalationRecipients) {
        this.escalationRecipients = escalationRecipients;
    }

    public boolean isSendEscalation() {
        return sendEscalation;
    }

    public void setSendEscalation(boolean sendEscalation) {
        this.sendEscalation = sendEscalation;
    }

    public boolean isSendInactive() {
        return sendInactive;
    }

    public void setSendInactive(boolean sendInactive) {
        this.sendInactive = sendInactive;
    }

    public boolean isInactiveOverride() {
        return inactiveOverride;
    }

    public void setInactiveOverride(boolean inactiveOverride) {
        this.inactiveOverride = inactiveOverride;
    }

    public List<RecipientListEntryBean> getInactiveRecipients() {
        return inactiveRecipients;
    }

    public void setInactiveRecipients(List<RecipientListEntryBean> inactiveRecipients) {
        this.inactiveRecipients = inactiveRecipients;
    }

    public boolean isIncludeSystemInfo(){
    	return this.includeSystemInfo;
    }
    public void setIncludeSystemInfo(boolean includeSystemInfo){
    	this.includeSystemInfo = includeSystemInfo;
    }
    
    public int getIncludePointValueCount() {
		return includePointValueCount;
	}

	public void setIncludePointValueCount(int includePointValueCount) {
		this.includePointValueCount = includePointValueCount;
	}
	
	public boolean isIncludeLogfile() {
		return includeLogfile;
	}

	public void setIncludeLogfile(boolean includeLogfile) {
		this.includeLogfile = includeLogfile;
	}
	
	public String getCustomTemplate() {
		return customTemplate;
	}
	
	public void setCustomTemplate(String customTemplate) {
		this.customTemplate = customTemplate;
	}
	
	public List<IntStringPair> getAdditionalContext() {
		return additionalContext;
	}
	
	public void setAdditionalContext(List<IntStringPair> additionalContext) {
		this.additionalContext = additionalContext;
	}

	@Override
	public void validate(ProcessResult response) {
		super.validate(response);
        if (activeRecipients.isEmpty())
            response.addGenericMessage("eventHandlers.noEmailRecips");

        if (sendEscalation) {
            if (escalationDelay <= 0)
                response.addContextualMessage("escalationDelay", "eventHandlers.escalDelayError");
            if (escalationRecipients.isEmpty())
                response.addGenericMessage("eventHandlers.noEscalRecips");
        }

        if (sendInactive && inactiveOverride) {
            if (inactiveRecipients.isEmpty())
                response.addGenericMessage("eventHandlers.noInactiveRecips");
        }
        
        List<String> varNameSpace = new ArrayList<String>();
        if(additionalContext != null){
	        for(IntStringPair cxt : additionalContext) {
	        	if(DataPointDao.instance.get(cxt.getKey()) == null)
	        		response.addGenericMessage("event.script.contextPointMissing", cxt.getKey(), cxt.getValue());
	        	
	        	String varName = cxt.getValue();
	            if (StringUtils.isBlank(varName)) {
	                response.addGenericMessage("validate.allVarNames");
	                break;
	            }
	
	            if (!VarNames.validateVarName(varName)) {
	                response.addGenericMessage("validate.invalidVarName", varName);
	                break;
	            }
	
	            if (varNameSpace.contains(varName)) {
	                response.addGenericMessage("validate.duplicateVarName", varName);
	                break;
	            }
	
	            varNameSpace.add(varName);
	        }
        }else{
        	additionalContext = new ArrayList<>();
        }
	}
    
	//
    //
    // Serialization
    //
    private static final long serialVersionUID = -1;
    private static final int version = 3;

    private void writeObject(ObjectOutputStream out) throws IOException {
    	out.writeInt(version);
    	out.writeObject(activeRecipients);
        out.writeBoolean(sendEscalation);
        out.writeInt(escalationDelayType);
        out.writeInt(escalationDelay);
        out.writeObject(escalationRecipients);
        out.writeBoolean(sendInactive);
        out.writeBoolean(inactiveOverride);
        out.writeObject(inactiveRecipients);
        out.writeBoolean(includeSystemInfo);
        out.writeInt(includePointValueCount);
        out.writeBoolean(includeLogfile);
        SerializationHelper.writeSafeUTF(out, customTemplate);
        out.writeObject(additionalContext);
    }
	
    @SuppressWarnings("unchecked")
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        int ver = in.readInt();
        if (ver == 1) {
        	activeRecipients = (List<RecipientListEntryBean>) in.readObject();
            RecipientListEntryBean.cleanRecipientList(activeRecipients);
            sendEscalation = in.readBoolean();
            escalationDelayType = in.readInt();
            escalationDelay = in.readInt();
            escalationRecipients = (List<RecipientListEntryBean>) in.readObject();
            RecipientListEntryBean.cleanRecipientList(escalationRecipients);
            sendInactive = in.readBoolean();
            inactiveOverride = in.readBoolean();
            inactiveRecipients = (List<RecipientListEntryBean>) in.readObject();
            RecipientListEntryBean.cleanRecipientList(inactiveRecipients);
            includeSystemInfo = in.readBoolean();
            includePointValueCount = in.readInt();
            includeLogfile = in.readBoolean();
            customTemplate = null;
            additionalContext = new ArrayList<IntStringPair>();
        }
        else if (ver == 2) {
        	activeRecipients = (List<RecipientListEntryBean>) in.readObject();
            RecipientListEntryBean.cleanRecipientList(activeRecipients);
            sendEscalation = in.readBoolean();
            escalationDelayType = in.readInt();
            escalationDelay = in.readInt();
            escalationRecipients = (List<RecipientListEntryBean>) in.readObject();
            RecipientListEntryBean.cleanRecipientList(escalationRecipients);
            sendInactive = in.readBoolean();
            inactiveOverride = in.readBoolean();
            inactiveRecipients = (List<RecipientListEntryBean>) in.readObject();
            RecipientListEntryBean.cleanRecipientList(inactiveRecipients);
            includeSystemInfo = in.readBoolean();
            includePointValueCount = in.readInt();
            includeLogfile = in.readBoolean();
            customTemplate = SerializationHelper.readSafeUTF(in);
            additionalContext = new ArrayList<IntStringPair>();
        }
        else if (ver == 3) {
        	activeRecipients = (List<RecipientListEntryBean>) in.readObject();
            RecipientListEntryBean.cleanRecipientList(activeRecipients);
            sendEscalation = in.readBoolean();
            escalationDelayType = in.readInt();
            escalationDelay = in.readInt();
            escalationRecipients = (List<RecipientListEntryBean>) in.readObject();
            RecipientListEntryBean.cleanRecipientList(escalationRecipients);
            sendInactive = in.readBoolean();
            inactiveOverride = in.readBoolean();
            inactiveRecipients = (List<RecipientListEntryBean>) in.readObject();
            RecipientListEntryBean.cleanRecipientList(inactiveRecipients);
            includeSystemInfo = in.readBoolean();
            includePointValueCount = in.readInt();
            includeLogfile = in.readBoolean();
            customTemplate = SerializationHelper.readSafeUTF(in);
            additionalContext = (List<IntStringPair>) in.readObject();
        }
    }
    
    @Override
    public void jsonWrite(ObjectWriter writer) throws IOException, JsonException {
    	super.jsonWrite(writer);
        writer.writeEntry("activeRecipients", activeRecipients);
        writer.writeEntry("sendEscalation", sendEscalation);
        if (sendEscalation) {
            writer.writeEntry("escalationDelayType", Common.TIME_PERIOD_CODES.getCode(escalationDelayType));
            writer.writeEntry("escalationDelay", escalationDelay);
            writer.writeEntry("escalationRecipients", escalationRecipients);
        }
        writer.writeEntry("sendInactive", sendInactive);
        if (sendInactive) {
            writer.writeEntry("inactiveOverride", inactiveOverride);
            if (inactiveOverride)
                writer.writeEntry("inactiveRecipients", inactiveRecipients);
        }
        writer.writeEntry("includeSystemInformation", includeSystemInfo);
        writer.writeEntry("includePointValueCount", includePointValueCount);
        writer.writeEntry("includeLogfile", includeLogfile);
        writer.writeEntry("customTemplate", customTemplate);
        
        JsonArray context = new JsonArray();
        for(IntStringPair pnt : additionalContext) {
        	DataPointVO dpvo = DataPointDao.instance.get(pnt.getKey());
        	if(dpvo != null) {
        		JsonObject point = new JsonObject();
        		point.put("dataPointXid", dpvo.getXid());
        		point.put("contextKey", pnt.getValue());
        		context.add(point);
        	}
        }
        writer.writeEntry("additionalContext", context);
    }
    
    @SuppressWarnings("unchecked")
	@Override
    public void jsonRead(JsonReader reader, JsonObject jsonObject) throws JsonException {
    	super.jsonRead(reader, jsonObject);
    	
        String text = null;
    	TypeDefinition recipType = new TypeDefinition(List.class, RecipientListEntryBean.class);
        JsonArray jsonActiveRecipients = jsonObject.getJsonArray("activeRecipients");
        if (jsonActiveRecipients != null)
            activeRecipients = (List<RecipientListEntryBean>) reader.read(recipType, jsonActiveRecipients);

        Boolean b = jsonObject.getBoolean("sendEscalation");
        if (b != null)
            sendEscalation = b;

        if (sendEscalation) {
            text = jsonObject.getString("escalationDelayType");
            if (text != null) {
                escalationDelayType = Common.TIME_PERIOD_CODES.getId(text);
                if (escalationDelayType == -1)
                    throw new TranslatableJsonException("emport.error.invalid", "escalationDelayType", text,
                            Common.TIME_PERIOD_CODES.getCodeList());
            }

            Integer i = jsonObject.getInt("escalationDelay");
            if (i != null)
                escalationDelay = i;

            JsonArray jsonEscalationRecipients = jsonObject.getJsonArray("escalationRecipients");
            if (jsonEscalationRecipients != null)
                escalationRecipients = (List<RecipientListEntryBean>) reader.read(recipType,
                        jsonEscalationRecipients);
        }

        b = jsonObject.getBoolean("sendInactive");
        if (b != null)
            sendInactive = b;

        if (sendInactive) {
            b = jsonObject.getBoolean("inactiveOverride");
            if (b != null)
                inactiveOverride = b;

            if (inactiveOverride) {
                JsonArray jsonInactiveRecipients = jsonObject.getJsonArray("inactiveRecipients");
                if (jsonInactiveRecipients != null)
                    inactiveRecipients = (List<RecipientListEntryBean>) reader.read(recipType,
                            jsonInactiveRecipients);
            }
        }
        b = jsonObject.getBoolean("includeSystemInformation");
        if(b != null){
        	includeSystemInfo = b;
        }
        
        includePointValueCount = jsonObject.getInt("includePointValueCount", 0);
        
        b = jsonObject.getBoolean("includeLogfile");
        if(b != null){
        	includeSystemInfo = b;
        }
        
        customTemplate = jsonObject.getString("customTemplate");
        
        JsonArray context = jsonObject.getJsonArray("additionalContext");
        if(context != null) {
        	List<IntStringPair> additionalContext = new ArrayList<>();
        	for(JsonValue jv : context) {
        		JsonObject jo = jv.toJsonObject();
        		String dataPointXid = jo.getString("dataPointXid");
        		if(dataPointXid == null)
        			throw new TranslatableJsonException("emport.error.context.missing", "dataPointXid");
        		
        		DataPointVO dpvo = DataPointDao.instance.getByXid(dataPointXid);
        		if(dpvo == null)
        			throw new TranslatableJsonException("emport.error.missingPoint", dataPointXid);
        		
        		String contextKey = jo.getString("contextKey");
        		if(contextKey == null)
        			throw new TranslatableJsonException("emport.error.context.missing", "contextKey");
        		
        		additionalContext.add(new IntStringPair(dpvo.getId(), contextKey));
        	}
        	this.additionalContext = additionalContext;
        } else
        	this.additionalContext = new ArrayList<>();
    }
    
    @Override
    public EventHandlerRT<EmailEventHandlerVO> createRuntime(){
    	return new EmailHandlerRT(this);
    }

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.vo.event.AbstractEventHandlerVO#asModel()
	 */
	@Override
	public AbstractEventHandlerModel<?> asModel() {
		return new EmailEventHandlerModel(this);
	}
}
