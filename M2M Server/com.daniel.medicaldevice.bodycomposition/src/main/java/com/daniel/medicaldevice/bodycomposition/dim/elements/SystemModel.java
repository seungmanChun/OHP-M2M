package com.daniel.medicaldevice.bodycomposition.dim.elements;

public class SystemModel extends Compound {
	private ElementsTypes elementType;

	public SystemModel(String deviceName) {
		super(deviceName);

		// TODO Auto-generated constructor stub
		this.elementType = ElementsTypes.SYSTEM_MODEL;

	}

	public ElementsTypes getElementType() {
		return elementType;
	}
}
