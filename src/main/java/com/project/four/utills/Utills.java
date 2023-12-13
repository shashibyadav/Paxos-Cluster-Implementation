package com.project.four.utills;

import com.project.four.server.ProjectEnums.MethodType;
import com.project.four.server.ProjectEnums.RequestKeys;

import java.util.HashMap;
import java.util.Map;

public class Utills {
	public static StringBuilder readDataFromBytes(byte[] buffer) {
		if (buffer == null) {
			return null;
		}
		StringBuilder temp = new StringBuilder();
		int i = 0;
		while (buffer[i] != 0) {
			temp.append((char) buffer[i]);
			i++;
		}
		return temp;
	}

	public static boolean isEmptyString(String input) {
		if (input == null || input == "") {
			return true;
		} else {
			return false;
		}
	}

	public static Map<RequestKeys, Object> generateRequest(MethodType type, Map<String, Object> payload, String authId) {
		Map<RequestKeys, Object> result = new HashMap<RequestKeys, Object>();
		result.put(RequestKeys.type, type);
		result.put(RequestKeys.data, payload);
		result.put(RequestKeys.authId, authId);
		return result;
	}

	@SuppressWarnings({ "unchecked" })
	public static Map<String, Object> getRequestData(Map<RequestKeys, Object> request) {
		return (Map<String, Object>) request.get(RequestKeys.data);
	}

	public static boolean checkInterrupt() {
		return Thread.currentThread().isInterrupted();
	}
}
