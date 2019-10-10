package org.fusesource.mqtt.client;

import org.fusesource.mqtt.codec.CONNACK;

import java.io.IOException;

public class MQTTException extends IOException {
  public final CONNACK connack;

  public MQTTException(String msg, CONNACK connack) {
    super(msg);
    this.connack = connack;
  }
}
