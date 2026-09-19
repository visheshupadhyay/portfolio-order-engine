package com.vishesh.orderengine;

import jakarta.validation.constraints.NotBlank;

/* Incoming POST /orders JSON. @Valid activates this boundary validation before controller logic runs. */
public record CreateOrderRequest(@NotBlank String id){
    
}
