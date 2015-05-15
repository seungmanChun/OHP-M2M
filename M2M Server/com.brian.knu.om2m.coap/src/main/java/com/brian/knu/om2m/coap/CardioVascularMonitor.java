package com.brian.knu.om2m.coap;

import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.om2m.commons.resource.Application;
import org.eclipse.om2m.commons.resource.Container;
import org.eclipse.om2m.commons.resource.ContentInstance;
import org.eclipse.om2m.commons.resource.StatusCode;
import org.eclipse.om2m.commons.rest.RequestIndication;
import org.eclipse.om2m.commons.rest.ResponseConfirm;
import org.eclipse.om2m.core.service.SclService;

import com.brian.knu.om2m.coap.device.CardioVascularDevice;
import com.brian.knu.om2m.coap.dim.DomainInformationModel;
import com.brian.knu.om2m.coap.dim.elements.Compound;
import com.brian.knu.om2m.coap.dim.elements.DevConfigurationID;
import com.brian.knu.om2m.coap.dim.elements.Simple;
import com.brian.knu.om2m.coap.dim.elements.SystemID;
import com.brian.knu.om2m.coap.dim.elements.SystemModel;
import com.brian.knu.om2m.coap.dim.elements.SystemType;
import com.brian.knu.om2m.coap.dim.elements.SystemTypeSpecList;

/**
 * The CardioVascularMonitor class creates one cardioVascular application called
 * "COAP_CARDIOVASCULAR". It creates one container for the application:
 * "DESCRIPTOR" container to store the description and "DATA" container to store
 * the measurements. For each container the right oBIX XML payload is created as
 * contentInstance. The starts a thread to listen to the application data. Once
 * a new data is detected, the Monitor creates a new contentInstance including
 * the measurement representation.
 * 
 */
public class CardioVascularMonitor {

	private static SclService core; // This is the service capability layer that
									// is provided by OM2M.
	private static String sclId = System.getProperty(
			"org.eclipse.om2m.sclBaseId", ""); // This gets the service
												// capability layer(scl) Id.
	private static String reqEntity = System.getProperty(
			"org.eclipse.om2m.adminRequestingEntity", ""); // This request for
															// the entity

	public static String tempId = "CoapSample";
	public static String sensorId = "COAP_CARDIOVASCULAR";
	public static int sensorValue = 0;

	public CardioVascularMonitor(SclService sclService) {
		core = sclService;
	}

	public void start() {
		// Listen for the data from the cardioVascular
		listenToCV();
		// Create required resources needed.
		createCVResources();
	}

	public void createCVResources() {
		String targetId, content;

		// Create the COAP_CARDIOVASCULAR application
		targetId = sclId + "/applications";
		ResponseConfirm response = core.doRequest(new RequestIndication(
				"CREATE", targetId, reqEntity,
				new Application(sensorId, tempId)));

		// get ACK from SCL means the Application created.
		if (response.getStatusCode().equals(StatusCode.STATUS_CREATED)) {

			// Create the "DESCRIPTOR" container to store the description
			targetId = sclId + "/applications/" + sensorId + "/containers";

			core.doRequest(new RequestIndication("CREATE", targetId, reqEntity,
					new Container("DESCRIPTOR")));

			// Create the "DATA" container to store the data
			core.doRequest(new RequestIndication("CREATE", targetId, reqEntity,
					new Container("DATA")));

			// Create the description contentInstance
			content = CardioVascularDevice.getDescriptiorRep(sclId, sensorId,
					tempId);
			targetId = sclId + "/applications/" + sensorId
					+ "/containers/DESCRIPTOR/contentInstances";
			core.doRequest(new RequestIndication("CREATE", targetId, reqEntity,
					new ContentInstance(content.getBytes())));

			// Create the data contentInstance
			content = CardioVascularDevice.getDIMRep(sensorId, null);
			targetId = sclId + "/applications/" + sensorId
					+ "/containers/DATA/contentInstances";
			core.doRequest(new RequestIndication("CREATE", targetId, reqEntity,
					new ContentInstance(content.getBytes())));
		}
	}

	private String dimXMLStr;
	private DomainInformationModel cvDIM;
	private CoapHandler myCardioVascularHandler = new CoapHandler() {
		@Override
		public void onLoad(CoapResponse response) {
			String content = response.getResponseText(); // Gets the response
															// from Coap
			dimXMLStr = content;

			try {
				cvDIM = new DomainInformationModel("CardioVascular DS");
				readXML(content);

				String contentData = CardioVascularDevice.getDIMRep(sensorId,
						cvDIM);
				String targetID = sclId + "/applications/" + sensorId
						+ "/containers/DATA/contentInstances";
				core.doRequest(new RequestIndication("CREATE", targetID,
						reqEntity, contentData));

				System.out.println("DIM :" + " GET DIM " + "\n");
			} catch (Exception e) {
				// TODO: handle exception
				e.printStackTrace();
			}
		}

		@Override
		public void onError() {
			System.err.println("-Failed--------");
		}

		private void readXML(String xml) throws DocumentException {
			Document document = DocumentHelper.parseText(xml);
			org.dom4j.Element root = document.getRootElement();
			getElements(root);
		}

		private void getElements(Element root) {
			if (root.elements().size() > 0) {
				for (Element ele : root.elements()) {
					if (ele.getName().equalsIgnoreCase("meta")) {
						distractElements(ele);
					}
					getElements(ele);
				}
			}
		}

		private void makeCompound(Compound cmp, Element ele) {
			List<Element> entries = ele.getParent().getParent()
					.elements("compound").get(0).elements("entries").get(0)
					.elements();

			for (Element entry : entries) {
				String name = entry.elements().get(0).elements("name").get(0)
						.getTextTrim();
				String type = entry.elements().get(0).elements("type").get(0)
						.getTextTrim();
				String value = entry.elements().get(0).elements("value").get(0)
						.getTextTrim();
				Simple smp = new Simple(name, type, value);
				cmp.addSimple(smp);

			}

		}

		private void distractElements(Element ele) {
			String deviceName = "CardioVascular";
			switch (Integer.valueOf(ele.getText())) {
			case 2438:
				Compound systemType = new SystemType(deviceName);

				makeCompound(systemType, ele);
				cvDIM.addCompound(systemType);
				break;
			case 2344:

				Compound systemModel = new SystemModel(deviceName);

				makeCompound(systemModel, ele);
				cvDIM.addCompound(systemModel);
				break;

			case 2436:

				String name = ele.getParent().getParent().elements("simple")
						.get(0).elements("name").get(0).getTextTrim();
				String type = ele.getParent().getParent().elements("simple")
						.get(0).elements("type").get(0).getTextTrim();
				String value = ele.getParent().getParent().elements("simple")
						.get(0).elements("value").get(0).getTextTrim();
				Compound systemId = new SystemID(deviceName);
				Simple smp = new Simple(name, type, value);
				systemId.addSimple(smp);

				cvDIM.addCompound(systemId);
				break;
			case 2628:
				String name1 = ele.getParent().getParent().elements("simple")
						.get(0).elements("name").get(0).getTextTrim();
				String type1 = ele.getParent().getParent().elements("simple")
						.get(0).elements("type").get(0).getTextTrim();
				String value1 = ele.getParent().getParent().elements("simple")
						.get(0).elements("value").get(0).getTextTrim();
				Compound devConfigurationID = new DevConfigurationID(deviceName);
				Simple sm = new Simple(name1, type1, value1);
				devConfigurationID.addSimple(sm);

				cvDIM.addCompound(devConfigurationID);
				break;
			case 2650:
				Compound systemTypeSpecList = new SystemTypeSpecList(deviceName);

				makeCompound(systemTypeSpecList, ele);
				cvDIM.addCompound(systemTypeSpecList);
				break;
			default:
				break;
			}
		}

	};

	public void listenToCV() {
		new Thread() {

			public void ObserveCardioVascular(String uri) {

				CoapClient client = new CoapClient(uri
						+ "/cardiovascularmonitor"); // This creates a coap
														// client to listen to
														// the cardioVascular.

				CoapObserveRelation relation1 = client
						.observe(myCardioVascularHandler);
			}

			@Override
			public void run() {
				// TODO Auto-generated method stub

				ObserveCardioVascular("coap://192.168.1.12:5683");

			}

		}.start();
	}
}
