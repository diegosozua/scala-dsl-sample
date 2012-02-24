/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package spring.integration.scala.dsl

import org.junit.Test
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.SpelParserConfiguration
import org.springframework.integration.dsl.DSL.anyComponent
import org.springframework.integration.dsl.DSL.pollableChannelComponent
import org.springframework.integration.dsl.builders.Channel
import org.springframework.integration.dsl.builders.PubSubChannel
import org.springframework.integration.dsl.builders.enrich
import org.springframework.integration.dsl.builders.handle
import org.springframework.integration.dsl.builders.http
import org.springframework.integration.dsl.builders.jms
import org.springframework.integration.dsl.builders.poll
import org.springframework.integration.dsl.builders.transform
import org.springframework.integration.dsl.utils.DslUtils
import org.springframework.integration.Message
import org.springframework.jms.connection.CachingConnectionFactory
import org.springframework.jms.core.JmsTemplate
import org.springframework.jms.core.MessageCreator
import javax.jms.Session
import javax.jms.TextMessage

/**
 * @author Oleg Zhurakousky
 */
class DSLUsageTests {

  @Test
  def demoSend = {
    val messageFlow =
      transform.using { m: Message[String] => m.getPayload().toUpperCase() } -->
      handle.using { m: Message[_] => println(m) }

    messageFlow.send("hello")
    println("done")
  }
  
  @Test
  def demoSendWithExplicitDirectChannel = {
    val messageFlow =
      Channel("direct") -->
      transform.using { m: Message[String] => m.getPayload().toUpperCase() } -->
      handle.using { m: Message[_] => println(m) }

    messageFlow.send("hello")
    println("done")
  }
  
  @Test
  def demoSendWithExplicitPubSubChannelOneSubscriber = {
    val messageFlow =
      PubSubChannel("direct") -->
      transform.using { m: Message[String] => m.getPayload().toUpperCase() } -->
      handle.using { m: Message[_] => println(m) }

    messageFlow.send("hello")
    println("done")
  }
  
  @Test
  def demoSendWithExplicitPubSubChannelMultipleSubscriber = {
    val messageFlow =
      PubSubChannel("direct") --< (
	      transform.using { m: Message[String] => m.getPayload().toUpperCase() } -->
	      handle.using { m: Message[_] => println("Subscriber-1 - " + m) }
	      ,
	      transform.using { m: Message[String] => m.getPayload().toUpperCase() } -->
	      handle.using { m: Message[_] => println("Subscriber-2 - " + m) }
      )

    messageFlow.send("hello")
    println("done")
  }

  @Test
  def sdemoSendAndReceive = {
    val messageFlow =
      transform.using { m: Message[String] => m.getPayload().toUpperCase() } -->
        handle.using { m: Message[_] => println(m); m }

    val reply = messageFlow.sendAndReceive[String]("hello")
    println(reply)
  }

  @Test
  def demoSendWithPubSubChannel = {
    val messageFlow =
      transform.using { m: Message[String] => m.getPayload().toUpperCase() }.where(name="myTransformer") -->
        PubSubChannel("pubSub") --< (
          transform.using { m: Message[_] => m.getPayload() + " - subscriber-1" } -->
          handle.using { m: Message[_] => println(m) },
          transform.using { m: Message[_] => m.getPayload() + " - subscriber-2" } -->
          handle.using { m: Message[_] => println(m) })

    println(messageFlow)
    println(DslUtils.toProductList(messageFlow))      
    messageFlow.send("hello")
    println("done")
  }

  @Test
  def demoSendWithBridge = {
    val messageFlow =
      Channel("A") -->
        Channel("B") -->
        handle.using { m: Message[_] => println("From Hello channel - " + m) }

    messageFlow.send("hello")

    println("done")
  }

  @Test
  def demoSendWithPolingBridge = {
    val messageFlow =
      Channel("A") -->
        Channel("B").withQueue --> poll.usingFixedRate(1) -->
        handle.using { m: Message[_] => println("From Hello channel - " + m) }

    messageFlow.send("hello")
    Thread.sleep(1000)
    println("done")
  }

  @Test
  def headerEnricherWithTuple = {
    val enricherA = enrich.header("hello" -> "bye") --> handle.using { m: Message[_] => println(m) }
    enricherA.send("Hello")
    println("done")
  }

  @Test
  def headerEnricherWithFunctionAsValue = {
    val enricherB = enrich.header("hello" -> Some({ m: Message[String] => m.getPayload().toUpperCase() })) --> handle.using { m: Message[_] => println(m) }
    enricherB.send("Hello")
    println("done")
  }

  @Test
  def headerEnricherWithMessageFunctionAsProcessor = {
    val enricherB = enrich.header("hello" -> { m: Message[String] => m.getPayload().toUpperCase() }) --> handle.using { m: Message[_] => println(m) }
    enricherB.send("Hello")
    println("done")
  }

  @Test
  def headerEnricherWithStringA = {
    val enricherB = enrich.header("hello" -> { "boo" + "bar" }) --> handle.using { m: Message[_] => println(m) }
    enricherB.send("Hello")
    println("done")
  }

  @Test
  def headerEnricherWithStringB = {
    val enricherB = enrich.header("hello" -> "foo") --> handle.using { m: Message[_] => println(m) }
    enricherB.send("Hello")
    println("done")
  }

  @Test
  def headerEnricherWithExpression = {
    val expression = new SpelExpressionParser(new SpelParserConfiguration(true, true)).parseExpression("(2 * 6) + ' days of Christmas'");
    val enricherB = enrich.header("phrase" -> expression) --> handle.using { m: Message[_] => println(m) }
    enricherB.send("Hello")
    println("done")
  }

  @Test
  def headerEnricherWithMultiTuple = {
    val expression = new SpelExpressionParser(new SpelParserConfiguration(true, true)).parseExpression("(2 * 6) + ' days of Christmas'");
    val enricher =
      enrich.headers("foo" -> "foo",
        "bar" -> { m: Message[String] => m.getPayload().toUpperCase() },
        "phrase" -> expression) -->
        handle.using { m: Message[_] => println(m) }

    enricher.send("Hello")
    println("done")
  }

  @Test
  def contentEnricher = {
    val employee = new Employee("John", "Doe", 23)
    val enricher =
      enrich { p: Person => p.name = employee.firstName + " " + employee.lastName; p.age = employee.age; p } -->
        handle.using { m: Message[_] => println(m) }

    enricher.send(new Person)
    println("done")
  }

  @Test
  def contentEnricherWithSubFlow = {

    val employeeBuldingFlow =
      transform.using { attributes: List[String] => new Employee(attributes(0), attributes(1), Integer.parseInt(attributes(2))) }

    val enricher =
      enrich { p: Person =>
        val employee = employeeBuldingFlow.sendAndReceive[Employee](List[String]("John", "Doe", "25"))
        p.name = employee.firstName + " " + employee.lastName
        p.age = employee.age
        p
      } -->
      handle.using { m: Message[_] => println(m) }

    enricher.send(new Person)
    println("done")
  }

  case class Person(var name: String = null, var age: Int = 0)

  class Employee(val firstName: String, val lastName: String, val age: Int)

  @Test
  def httpOutboundWithFunctionUrl = {

    val tickerService =
      transform.using { s: String =>
        s.toLowerCase() match {
          case "vmw" => "VMWare"
          case "orcl" => "Oracle"
          case _ => "vmw"
        }
      }

    val httpFlow = 
            enrich.header("company" -> {name:String => tickerService.sendAndReceive[String](name)}) -->
    		http.GET[String] { m: Message[String] => "http://www.google.com/finance/info?q=" + m.getPayload()} -->
    		handle.using{quotes:Message[_] => println("QUOTES for " + quotes.getHeaders().get("company") + " : " + quotes)}
    		
    httpFlow.send("vmw")
     
    println("done")
  }
  
  @Test
  def httpOutboundWithStringUrl = {

    val tickerService =
      transform.using { s: String =>
        s.toLowerCase() match {
          case "vmware" => "vmw"
          case "oracle" => "orcl"
          case _ => ""
        }
      }

    val httpFlow = 
    		http.GET[String]("http://www.google.com/finance/info?q=" + tickerService.sendAndReceive[String]("Oracle")) -->
    		handle.using{quotes:Message[_] => println("QUOTES for " + quotes.getHeaders().get("company") + " : " + quotes)}
    		
    httpFlow.send("static")
    
    println("done")
  }
  
  @Test
  def jmsInboundGateway  = {
    val connectionFactory = JmsDslTestUtils.localConnectionFactory
    
    val flow = 
      jms.listen(requestDestinationName = "myQueue", connectionFactory = connectionFactory) -->
      handle.using{m:Message[_] => println("logging existing message and passing through " + m); m} -->
      transform.using{value:String => value.toUpperCase()}
    
    flow.start  
    
    val jmsTemplate = new JmsTemplate(connectionFactory);
    val request = new org.apache.activemq.command.ActiveMQQueue("myQueue")
    val reply = new org.apache.activemq.command.ActiveMQQueue("myReply")
    jmsTemplate.send(request, new MessageCreator {
		def  createMessage(session:Session) = {
			val message = session.createTextMessage();
			message.setText("Hello from JMS");
			message.setJMSReplyTo(reply);
			message;
		}
	});
    
    val replyMessage = jmsTemplate.receive(reply);
    println("Reply Message: " + replyMessage.asInstanceOf[TextMessage].getText())
   
    flow.stop
    println("done")
  }
}