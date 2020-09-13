/**
 *  Sonoff Zigbee Button
 *
 *  Copyright 2020 Stanislav Avdeev
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */

import groovy.json.JsonOutput
import physicalgraph.zigbee.zcl.DataType

metadata {
	definition (name: "Sonoff Zigbee Button", namespace: "stratosnn", author: "stratosnn", cstHandler: true, ocfDeviceType: "x.com.st.d.remotecontroller") {
		capability "Button"
        capability "Holdable Button"
        capability "Battery"
        capability "Sensor"
        capability "Configuration"
        
        // eWeLink
		fingerprint manufacturer: "eWeLink", model: "WB01", deviceJoinName: "eWeLink Button"
	}


	simulator {
		// TODO: define status and reply messages here
	}

	tiles {
        standardTile("button", "device.button", width: 2, height: 2) {
			state "default", label: "", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#ffffff"
			state "button 1 pushed", label: "pushed #1", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#00A0DC"
		}
        valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false) {
			state "battery", label:'${currentValue}% battery', unit:""
		}

		main (["button"])
		details(["button", "battery"])
	}
}

def installed() {
    sendEvent(name: "supportedButtonValues", value: ["pushed","held","double"].encodeAsJSON(), displayed: false)
    sendEvent(name: "numberOfButtons", value: 1, displayed: false)
    sendEvent(name: "button", value: "pushed", data: [buttonNumber: 1], displayed: false)

	// // These devices don't report regularly so they should only go OFFLINE when Hub is OFFLINE
	// sendEvent(name: "DeviceWatch-Enroll", value: JsonOutput.toJson([protocol: "zigbee", scheme:"untracked"]), displayed: false)
}

// parse events into attributes
def parse(String description) {
    // displayDebugLog(": Parsing '${description}'")
	log.debug "Parsing '${description}'"

    def event = [:]

    if ((description?.startsWith("catchall:")) || (description?.startsWith("read attr -"))) {
        def descMap = zigbee.parseDescriptionAsMap(description)
        log.debug "desc as map: '${descMap}'"
        if (descMap.clusterInt == zigbee.POWER_CONFIGURATION_CLUSTER && descMap.attrInt == 0x0021) {
            event = getBatteryEvent(zigbee.convertHexToInt(descMap.value) / 2)
        } else if (descMap.clusterInt == zigbee.ONOFF_CLUSTER) {
            event = getButtonEvent(descMap)
        }
    }

    def result = []
    if (event) {
        log.debug "Creating event: ${event}"
        result = createEvent(event)
    } else {
        log.warn "Unknown event '${descMap}'"
    }

    return result
}

def configure() {
	log.debug "Configuring device ${device.getDataValue("model")}"

    def cmds = []
    // cmds << zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0001)
    // cmds << zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0004)
    // cmds << zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0005)
    // cmds << zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0006)
    cmds << zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x21, DataType.UINT8, 30, 21600, 0x01)
    cmds << zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x20, DataType.UINT8, 30, 21600, 0x01)
	cmds << zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x20)
	cmds << zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x21)
    cmds << zigbee.addBinding(zigbee.ONOFF_CLUSTER)
	cmds += readDeviceBindingTable() // Need to read the binding table to see what group it's using

    // def cmds = zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x21) +
	// 		zigbee.addBinding(zigbee.ONOFF_CLUSTER) +
	// 		readDeviceBindingTable() 

	cmds
}

private List readDeviceBindingTable() {
	["zdo mgmt-bind 0x${device.deviceNetworkId} 0",
	 "delay 200"]
}

private Map getBatteryEvent(value) {
	def result = [:]
	result.value = value
	result.name = 'battery'
	result.descriptionText = "${device.displayName} battery was ${result.value}%"
	return result
}

private Map getButtonEvent(descMap) {
    if (descMap.commandInt == 0x02) {
        return getButtonResult("pushed")
    } else if (descMap.commandInt == 0x01) {
        return getButtonResult("double")
    } else if (descMap.commandInt == 0x00) {
        return getButtonResult("held")
    } else {
        log.warn "Unknown button press"
    }
}

private Map getButtonResult(value) {
    def descriptionText
    if (value == "pushed")
        descriptionText = "${ device.displayName } was pushed"
    else if (value == "held")
        descriptionText = "${ device.displayName } was held"
    else
        descriptionText = "${ device.displayName } was pushed twice"
    return [
            name           : 'button',
            value          : value,
            descriptionText: descriptionText,
            translatable   : true,
            isStateChange  : true,
            data           : [buttonNumber: 1]
    ]
}