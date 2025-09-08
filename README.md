# AI Middleware - Java

A lightweight Java middleware project for AI inference, providing **NSFW image moderation APIs** and a **gRPC inference service**.

---

## Features
- REST APIs with Spring Boot (sync, async, and reactive WebFlux endpoints)  
- gRPC client & server for image classification 
- ONNX model loading with DJL (Deep Java Library)
- Basic monitoring with Micrometer  

---

## Requirements
- Java **17+**  
- Maven or Gradle  
- ONNX model file (`nsfw.onnx`) + labels file (`labels.txt`)  

> ⚠️ The model file is **not included** (too large for GitHub).  
> Please download manually and place under: src/main/resources/models/

---

## Run
```bash
mvn spring-boot:run
```
API runs at: `http://localhost:8080`

Example: `curl -X POST http://localhost:8080/api/moderation/check \
  -F "file=@test.jpg"
`

