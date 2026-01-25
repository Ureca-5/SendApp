package com.mycom.myapp.sendapp.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SendResponse {
	
	private final String status;
	private final String errorMessage;
	
	public static SendResponse success() {
        return new SendResponse("SENT", null);
    }

    public static SendResponse fail(String message) {
        return new SendResponse("FAILED", message);
    }
}
