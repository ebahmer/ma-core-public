/*
    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
    @author Matthew Lohbihler
 */
package com.serotonin.m2m2.rt.event.detectors;

import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.rt.dataImage.PointValueTime;
import com.serotonin.m2m2.view.text.TextRenderer;
import com.serotonin.m2m2.vo.event.detector.BinaryStateDetectorVO;

public class BinaryStateDetectorRT extends StateDetectorRT<BinaryStateDetectorVO> {
	
    public BinaryStateDetectorRT(BinaryStateDetectorVO vo) {
        super(vo);
    }

    @Override
    public TranslatableMessage getMessage() {
        String name = vo.njbGetDataPoint().getExtendedName();
        String prettyText = vo.njbGetDataPoint().getTextRenderer()
                .getText(vo.isState(), TextRenderer.HINT_SPECIFIC);
        TranslatableMessage durationDescription = getDurationDescription();

        if (durationDescription == null)
            return new TranslatableMessage("event.detector.state", name, prettyText);
        return new TranslatableMessage("event.detector.periodState", name, prettyText, durationDescription);
    }

    @Override
    protected boolean stateDetected(PointValueTime newValue) {
        boolean newBinary = newValue.getBooleanValue();
        return newBinary == vo.isState();
    }

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.util.timeout.TimeoutClient#getThreadName()
	 */
	@Override
	public String getThreadNameImpl() {
		return "BinaryState Detector " + this.vo.getXid();
	}

}
