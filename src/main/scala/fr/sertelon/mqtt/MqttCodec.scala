package fr.sertelon.mqtt

import akka.actor._
import akka.io._
import akka.util._
import handler._
import model._
import model.MqttMessage._

object MqttCodec {
  
  // Encoders
  def encodeMqttMessage(msg: MqttMessage) : ByteString = {
		import akka.util.ByteStringBuilder
		  
    val header1 = 0
    
    
    
    ByteString()  
  }
  
  
  
  // Decoders
  def decodeMqttMessage(bs: ByteString): MqttMessage = {
    val it = bs.iterator
    val firstByte = it.getByte

    val messageType = (firstByte & 0xF0) >>> 4
    val duplicate = (firstByte & 0x8) >>> 3
    val qos = (firstByte & 0x6) >>> 1
    val retain = firstByte & 0x1
    val remainingLength = getRemaininLength(it)

    val header = MqttHeader(messageType, duplicate, qos, retain, remainingLength)

    header.messageType match {
      case ConnectMessageType => decodeConnectMessage(header, it)
      case ConnAckMessageType => null
      case PublishMessageType => decodePublishMessage(header, it)
      case PubAckMessageType => null
      case PubRecMessageType => null
      case PubRelMessageType => null
      case PubCompMessageType => null
      case SubscribeMessageType => null
      case SubAckMessageType => null
      case UnsubscribeMessageType => null
      case UnsubAckMessageType => null
      case PingReqMessageType => null
      case PingRespMessageType => null
      case DisconnectMessageType => null
      case UnknownMessageType => null

    }
  }

  private def decodeConnectMessage(header: MqttHeader, it: ByteIterator) = {
    val protocolName = getUTF8String(it)._2
    val versionNumber: Int = it.getByte

    // Connect flags
    val flagByte = it.getByte
    val cleanSession = (flagByte & 0x02) >>> 1
    val hasWill = (flagByte & 0x04) >>> 2
    val willQoS = (flagByte & 0x18) >>> 3
    val hasWillRetain = (flagByte & 0x20) >>> 5
    val hasPassword = (flagByte & 0x40) >>> 6
    val hasUsername = (flagByte & 0x80) >>> 7

    val flags = ConnectFlags(hasUsername, hasPassword, hasWillRetain, willQoS, hasWill, cleanSession)
    val keepAlive = (it.getByte >>> 8) + it.getByte

    val clientId = getUTF8String(it)._2
    val willTopic = if (hasWill) Some(getUTF8String(it)._2) else None
    val willMessage = if (hasWill) Some(getUTF8String(it)._2) else None
    val username = if (hasUsername) Some(getUTF8String(it)._2) else None
    val password = if (hasPassword) Some(getUTF8String(it)._2) else None

    MqttConnect(header, ConnectHeader(protocolName, versionNumber, flags, keepAlive), ConnectPayload(clientId, willTopic, willMessage, username, password))
  }

  private def decodePublishMessage(header: MqttHeader, it: ByteIterator) = {
    val (topicLength, topic) = getUTF8String(it)
    val msgId = ((it.getByte >>> 8) + it.getByte).toChar

    val payloadLength = header.length - topicLength - 2
    
    val payload = Some(it.take(payloadLength).toByteString.toArray)

    MqttPublish(header, PublishHeader(topic, msgId), payload)
  }

  // Utils

  private def getUTF8String(it: ByteIterator): (Int, String) = {
    val strLength = (it.getByte >>> 8) + it.getByte
    val bytes = new Array[Byte](strLength)
    it getBytes bytes
    (strLength + 2, ByteString(bytes).utf8String)
  }

  private implicit def intToBoolean(i: Int) = if (i == 1) true else false

  private def getRemaininLength(it: ByteIterator) = {
    // Fully var because it's how it's presented in spec
    var multiplier = 1
    var value = 0

    var currentByte = 0

    do {
      currentByte = it.getByte

      value = value + (currentByte & 127) * multiplier
      multiplier = multiplier * 128
    } while ((currentByte & 128) != 0)

    value
  }
}