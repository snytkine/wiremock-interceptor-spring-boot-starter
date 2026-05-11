# WireMock Middleware for Spring Boot RestClient

## Project Overview

This is a Spring Boot auto-configuration module that provides WireMock integration for testing HTTP clients. It allows developers to intercept outgoing HTTP requests from Spring's RestTemplate and mock responses using WireMock stubs, without requiring actual network calls. This enables fast, reliable testing of external service integrations.

### Key Technologies Used
- **Spring Boot**: Core framework for dependency injection and auto-configuration
- **WireMock**: HTTP mocking library for creating stub responses  
- **Java 21+**: Language and standard libraries
- **Lombok**: For reducing boilerplate code in model classes

### Architecture
The middleware works as a `ClientHttpRequestInterceptor` that sits between Spring's RestTemplate and external HTTP services. During intercept, requests are matched against WireMock stubs; if a match is found, the mock response is returned; otherwise, the request continues to the actual service.

## Getting Started

### Prerequisites
- Java 21 or higher
- Maven or Gradle build system
- Spring Boot application

### Installation
Add this dependency to your `pom.xml`:
#### Important - you must also provide dependency on wiremock and it should ideally be version 3.10.0.
as at this time it will not work with higher version of wiremock.
```xml
<dependency>
    <groupId>io.github.snytkine</groupId>
    <artifactId>wiremock-interceptor-spring-boot-starter</artifactId>
    <version>0.0.1</version>
</dependency>
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock</artifactId>
    <version>3.10.0</version>
</dependency>
```

With this extension, you can use Faker to generate random data in your mapping files.
For documentation of available Faker options look here:
[Faker Documentation](https://github.com/wiremock/wiremock-faker-extension/blob/main/docs/reference.md)


### Basic Usage
Enable the middleware in your `application.properties` or `application.yml`:
```yaml
# application.yml
io:
  github:
    snytkine:
      rest-client-wiremock-interceptor:
        enabled: true
        mock-response-header: X-Mock-Response
        mock-response-header-value: wiremock-middleware
        templating-enabled: true
        mappings-class-path: mocks
        stub-request-logging-disabled: true
```

### Explanation of important configuration properties
- enabled if set to false, the middleware will not be enabled.
- mappings-class-path the relative path under the src/main/resources in your Spring application. This is where your mapping files are located.
Inside that directory you should have at least one directory named `mappings` where your mapping files are located.
Optionally if you want to use separate json files for responses you can create a subdirectory named `__files` where your response files are located.
- mock-response-header if set, the middleware will add a header with the name specified in this property only if the response is mocked.
The value of this header will be the same you set in the next property.
- mock-response-header-value if set, the middleware will add a header with
the name specified in the previous property with the value.

The interceptor will automatically be registered with the Spring context 
a Bean with name restClientWiremockInterceptor implementing ClientHttpRequestInterceptor
This bean has the @Order(50) annotation which means that if you have other interceptors in your application you can add the @Order annotation to your own interceptors and then sort them by order in your component. Remember when list of beans is injected it is not automatically ordered by the @Order annotations. You can use @PostConstruct to ensure that by adding a method to sort injected list by Order annotations.

### Example of a client using the interceptor:
In this example a bean of type RestClient will be created and will have 
all beans that implement ClientHttpRequestInterceptor added to it. (including restClientWiremockInterceptor)

```java
@Configuration
public class RestClientConfig {
    
    @Bean
    public RestClient restClient(RestClient.Builder builder, 
                               Collection<ClientHttpRequestInterceptor> interceptors) {
        return builder
            .interceptors(interceptors)
            .build();
    }
}
```

### Example of a client using only restClientWiremockInterceptor with Spring Boot.

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Autowired
    private ClientHttpRequestInterceptor restClientWiremockInterceptor;
    
    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder
            .interceptors(restClientWiremockInterceptor)
            .build();
    }
}

```

### Now use RestClient bean anywhere in your application.

```java
// In your service class
@Service
public class MyService {
    
    private final RestClient restClient;
    
    public MyService(RestClient restClient) {
        this.restClient = restClient;
    }
    
    public void makeRequest() {
        // This request will automatically go through all registered interceptors
        String response = restClient.get()
                .uri("https://api.example.com/data")
                .retrieve()
                .body(String.class);
    }
}
```

### Running Tests
To run tests, use one of the following commands:
- For Maven:
  ```bash
  ./mvnw test
  ```
- For Gradle:
  ```bash
  ./gradlew test
  ```


### Example Mapping File

Here's an example of a WireMock mapping file (`example.json`):

```json
{
  "request": {
    "method": "GET",
    "url": "/posts/2",
    "host": {
      "equalTo": "jsonplaceholder.typicode.com"
    }
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": {
      "userId": 1,
      "id": 2,
      "title": "My Mock Title 2",
      "body": "Hey there! This is mock data."
    }
  }
}
```

### Example of mocking with mock response stored in separate file and also using Faker extension
##### Place this file under src/main/resources/mocks
```json
{
  "request": {
    "method": "GET",
    "urlPathTemplate": "/posts/{postId}",
    "host": {
      "equalTo": "jsonplaceholder.typicode.com"
    }
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "bodyFileName": "response1.json",
    "transformers": ["response-template"]
  }
}
```

##### Create a file response1.json in the src/resources/mocks/__files directory with the following content:
```json
{
  "userId": "{{request.path.postId}}",
  "id": 3,
  "title": "THIS IS FROM FILE!!! postId is {{request.path.postId}} {{now timezone='America/New_York' format='yyyy-MM-dd HH:mm:ssZ'}} ",
  "body": "resopnse from bodyFileName pathSegments.[1]={{request.pathSegments.[1]}} Random name is {{ random 'Name.first_name' }}"
}
```
### Troubleshooting
- **Spring boot application does not start or crashes**
This extension depends you to provide the correct dependency of the wiremock package. This is done on purpose to avoid potential dependency conflicts in case your application is already using wiremock for testing.
This package works best with wiremock version 3.10.0 and during our testing we had trouble making it work with higer versions of wiremock. So ideally you need to provide wiremock version 3.10.0 so make sure you have this in your pom.xml
```xml
<dependency>
	<groupId>org.wiremock</groupId>
	<artifactId>wiremock</artifactId>
	<version>3.10.0</version>
</dependency>
```
- **Interceptor not working**: Ensure `enabled=true` is set in properties and the interceptor bean is created.
- **No mock responses**: Verify that mappings exist and are correctly formatted. Verify that value of mappings-class-path configuration is set to relative path under src/main/resources and that this directory exists and has subdirectory called `mappings` thats where your mappings should be located.

By following these steps, you can effectively use this package to intercept and mock HTTP requests in your Spring Boot application.

## For Contributors developers of this extension.
###  Project Structure

### Main Directories
- `src/main/java/net/snytkine/springboot/wiremock_middleware/`
  - Core middleware implementation classes
- `src/main/java/net/snytkine/springboot/wiremock_middleware/model/`
  - Configuration models and properties
- `src/main/java/net/snytkine/springboot/wiremock_middleware/autoconfigure/`
  - Spring Boot auto-configuration classes

### Key Files and Their Roles
1. **WireMockInterceptor.java** - Main interceptor implementation that handles request interception 
2. **WireMockInterceptorFactory.java** - Spring configuration class for creating the interceptor bean
3. **WireMockConfigurationFactory.java** - Configures WireMock server settings from properties
4. **WireMockProperties.java** - Configuration properties for the middleware behavior
5. **MockModel.java** - Example data model (created as requested)
6. **WireMockInterceptorAutoConfiguration.java** - Main auto-configuration class that ties everything together

## Development Workflow

### Coding Standards
- Follow Spring Boot conventions for auto-configuration and property binding
- Use Lombok annotations to reduce boilerplate code  
- Adhere to semantic versioning for releases
- Write comprehensive unit tests

### Testing Approach
The project includes unit tests that verify:
- The interceptor factory correctly creates interceptors 
- Properties are properly wired into the interceptor
- WireMock server integration works as expected

### Build and Deployment
Build with Maven or Gradle:
```bash
./mvnw clean package
```

### Contribution Guidelines
1. Fork the repository and create feature branches
2. Write comprehensive tests for new functionality  
3. Follow existing code style and patterns
4. Update documentation when making changes to APIs

## Key Concepts

### WireMock Interceptor Pattern
The middleware implements Spring's `ClientHttpRequestInterceptor` interface to intercept HTTP calls before they are sent over the network. This allows for transparent mocking without modifying client code.

### Configuration Properties
The middleware uses Spring Boot's configuration properties pattern to allow customization of behavior:
- `enabled`: Enable/disable the interceptor
- `mockResponseHeader`: Header to identify mock responses  
- `templatingEnabled`: Enable WireMock templates
- Various WireMock server configuration options

### Direct Call HTTP Server
Uses WireMock's `DirectCallHttpServer` which allows in-process request matching without network overhead, making tests fast and reliable.
