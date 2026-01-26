package com.mycom.myapp.sendapp.delivery.sender;

import com.mycom.myapp.sendapp.delivery.dto.DeliveryPayload;
import com.mycom.myapp.sendapp.delivery.dto.SendResponse;

public interface DeliverySender {
	boolean supports(String channel);
	SendResponse send(DeliveryPayload payload);
}
