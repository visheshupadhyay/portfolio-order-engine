package com.vishesh.orderengine.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/* Incoming POST /orders JSON. @Valid rejects malformed input before controller
 * logic or database work begins; the 100-character boundary mirrors VARCHAR(100). */
public record CreateOrderRequest(@NotBlank @Size(max = 100, message = "must not be greater than 100 characters") String id){
    
}
