/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.inatel.demos;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer09;
import org.apache.flink.streaming.util.serialization.JSONDeserializationSchema;
import org.apache.flink.util.Collector;
import java.util.Properties;

public class SpeedAvg {

  public static void main(String[] args) throws Exception {

   

    // create execution environment
    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

    Properties properties = new Properties();
    properties.setProperty("bootstrap.servers", "localhost:9092");
    properties.setProperty("group.id", "flink_consumer");
    
    DataStream stream = env.addSource(
            new FlinkKafkaConsumer09<>("flink-demo", 
            new JSONDeserializationSchema(), 
            properties)
    );
 

    stream  .flatMap(new TelemetryJsonParser())
            .keyBy(0)
            .timeWindow(Time.seconds(3))
            .reduce(new AvgReducer())
            .flatMap(new AvgMapper())
            .map(new AvgPrinter())
            .print();

    env.execute();
    }

    // FlatMap Function - Json Parser
    // Receive JSON data from Kafka broker and parse car number, speed and counter
    
    // {"Car": 9, "time": "52.196000", "telemetry": {"Vaz": "1.270000", "Distance": "4.605865", "LapTime": "0.128001", 
    // "RPM": "591.266113", "Ay": "24.344515", "Gear": "3.000000", "Throttle": "0.000000", 
    // "Steer": "0.207988", "Ax": "-17.551264", "Brake": "0.282736", "Fuel": "1.898847", "Speed": "34.137680"}}

    static class TelemetryJsonParser implements FlatMapFunction<ObjectNode, Tuple3<String, Float, Integer>> {
      @Override
      public void flatMap(ObjectNode jsonTelemetry, Collector<Tuple3<String, Float, Integer>> out) throws Exception {
        String carNumber = "car" + jsonTelemetry.get("Car").asText();
        //float speed = jsonTelemetry.get("telemetry").get("Speed").floatValue() * 18f/5f; // convert to km/h
        float gear = jsonTelemetry.get("telemetry").get("Gear").floatValue(); // getting gear value
        out.collect(new Tuple3<>(carNumber,  gear, 1 ));
      }
    }

    // Reduce Function - Sum samples and count
    // This funciton return, for each car, the sum of two speed measurements and increment a conter.
    // The counter is used for the average calculation.
    static class AvgReducer implements ReduceFunction<Tuple3<String, Float, Integer>> {
      @Override
      public Tuple3<String, Float, Integer> reduce(Tuple3<String, Float,Integer> value1, Tuple3<String, Float, Integer> value2) {
        return new Tuple3<>(value1.f0, value1.f1 + value2.f1, value1.f2+1);        
       /*
       float mudaMarcha=0;
        if(value1.f1 != value2.f1){
          if(value1.f1 > value2.f1){
            mudaMarcha=value1.f1-value2.f1;
          }else{
            mudaMarcha=value2.f1-value1.f1;
          }
        }
        
        return new Tuple3<>(value1.f0, value1.f1 + mudaMarcha, value1.f2+1); //calculating the gear changes*/
      }
    }

    // FlatMap Function - Average
    // Calculates the average
    static class AvgMapper implements FlatMapFunction<Tuple3<String, Float, Integer>, Tuple2<String, Float>> {
      @Override
      public void flatMap(Tuple3<String, Float, Integer> carInfo, Collector<Tuple2<String, Float>> out) throws Exception {
        //out.collect(  new Tuple2<>( carInfo.f0 , carInfo.f1/carInfo.f2 )  );
       
          
        out.collect(  new Tuple2<>( carInfo.f0 , carInfo.f1/carInfo.f2 )  );
      }
    }

    // Map Function - Print average    
    static class AvgPrinter implements MapFunction<Tuple2<String, Float>, String> {
      @Override
      public String map(Tuple2<String, Float> avgEntry) throws Exception {
        return  String.format("Avg Gear for %s : %.2f changed", avgEntry.f0 , avgEntry.f1 ) ;
      }
    }

  }
