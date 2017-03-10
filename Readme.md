# Base64 encoder

This directory contains the Java source code and pom.xml file required to
base64-encode a message payload, or to base64-decode a message payload.

## Using this policy

You do not need to build the source code in order to use the policy in Apigee Edge. 
All you need is the built JAR, and the appropriate configuration for the policy. 
If you want to build it, feel free.  The instructions are at the bottom of this readme. 


1. copy the jar file, available in  target/edge-custom-base64.jar , if you have built the jar, or in [the repo](bundle/apiproxy/resources/java/edge-custom-base64.jar) if you have not, to your apiproxy/resources/java directory. You can do this offline, or using the graphical Proxy Editor in the Apigee Edge Admin Portal. 

2. include an XML file for the Java callout policy in your
   apiproxy/resources/policies directory. It should look
   like this:  
   ```xml
    <JavaCallout name='Java-Base64-1'>
        ...
      <ClassName>com.dinochiesa.edgecallouts.Base64</ClassName>
      <ResourceURL>java://edge-custom-base64.jar</ResourceURL>
    </JavaCallout>
   ```  

3. use the Edge UI, or a command-line tool like [pushapi](https://github.com/carloseberhardt/apiploy) or [apigeetool](https://github.com/apigee/apigeetool-node) or similar to
   import the proxy into an Edge organization, and then deploy the proxy . 
   Eg,    
   ```./pushapi -v -d -o ORGNAME -e test -n base64-encoder ```

4. Use a client to generate and send http requests to the proxy you just deployed . Eg,   
   ```
  curl -i -X GET -H accept-encoding:base64 \
    https://ORGNAME-ENVNAME.apigee.net/base64-encoder/t1
   ```


## Notes on Usage

There is one callout class, com.dinochiesa.edgecallouts.Base64

It encodes the message content into Base64 format, or decodes Base64-encoded message content.
If you place it in the request flow, it will operate on the request content.
If you attach the policy to the response flow, it will operate on the response content. 


## Configuring the Callout

An example for encoding:

```xml
<JavaCallout name='Java-Base64Encode'>
  <Properties>
    <Property name='action'>encode</Property>
    <Property name='string-output'>true</Property>
  </Properties>
  <ClassName>com.dinochiesa.edgecallouts.Base64</ClassName>
  <ResourceURL>java://edge-custom-base64.jar</ResourceURL>
</JavaCallout>
```

These are the available configuration properties:

| property name     | status    | description                                              | 
| ----------------- |-----------|----------------------------------------------------------| 
| action            | Required  | possible values: encode, decode                          |
| string-output     | Optional  | Applies only on Encode. Default: true.                   |
| line-length       | Optional  | Applies only on Encode. Default: -1 (no line breaks).    |
| mime-type         | Optional  | Applies only on Decode. Default: none.                   |

The action determines what the Callout will do.
The result of the encode or decode operation is always places in a variable named 'b64_result'.

The string-output property tells the callout whether to instantiate a string from the Base64-encoded bytes.
By default it is true.  With a resulting string you can use AssignMessage like this:

```xml
<AssignMessage name='AM-ResponseString'>
  <Set>
    <Payload contentType='text/plain'>{b64_result}</Payload>
  </Set>
  <IgnoreUnresolvedVariables>true</IgnoreUnresolvedVariables>
  <AssignTo createNew='false' transport='http' type='response'/>
</AssignMessage>
```

And the plain-text result will look something like this (one long line):

```
iVBORw0KGgoAAAANSUhEUgAAAKEAAABRAQMAAACADVTsAAAABlBM....

```

The line-length is optional. Specify a positive integer value to have the callout emit
an encoded value with line breaks. For example IETF RFC 2045 specifies 76-character
line lengths. This is irrelevant when action = decode.


A decoding example:

```xml
<JavaCallout name='Java-Base64Decode'>
  <Properties>
    <Property name='action'>decode</Property>
    <Property name='mime-type'>image/png</Property>
  </Properties>
  <ClassName>com.dinochiesa.edgecallouts.Base64</ClassName>
  <ResourceURL>java://edge-custom-base64.jar</ResourceURL>
</JavaCallout>
```

When decoding, the output variable b64_result, is a byte[] . 
The mime-type property gets propagated to the variable b64_mimeType. It doesn't
actually affect what the Callout does with the data. It is intended for use by a
subsequent AssignMessage policy. For example:

```xml
<AssignMessage name='AM-ResponseStream'>
  <Set>
    <Headers>
      <Header name='content-type'>{b64_mimeType:application/octet-stream}</Header>
    </Headers>
    <!-- do not set payload here, when the output is a byte array -->
  </Set>
  <IgnoreUnresolvedVariables>true</IgnoreUnresolvedVariables>
  <AssignTo createNew='false' transport='http' type='response'/>
  <!-- must assign the variable "response.content" rather than the payload -->
  <AssignVariable>
    <Name>response.content</Name>
    <Ref>b64_result</Ref>
  </AssignVariable>
</AssignMessage>
```


## Example API Proxy

You can find an example proxy bundle that uses the policy, [here in this repo](bundle/apiproxy).

Invoke it like this:

```
  curl -i -X GET -H accept-encoding:base64 \
    https://ORGNAME-ENVNAME.apigee.net/base64-encoder/t1
```

This request uses a non-standard value for accept-encoding. 
You will notice that the response shows Content-type:image/png, but the response also includes a "non-standard" value for the content-encoding header.
```
Content-Encoding: Base64
```

To get line breaks when encoding, pass the query param:

```
  curl -i -X GET -H accept-encoding:base64 \
    https://ORGNAME-ENVNAME.apigee.net/base64-encoder/t1?linelength=76
```

And the result looks like this:

```
iVBORw0KGgoAAAANSUhEUgAAAKEAAABRAQMAAACADVTsAAAABlBMVEUiIiL///9ehyAxAAABrElE
QVR4Xu3QL2/bQBgG8NdRlrnMNqxu1eVAahCQVAEF03STbsuBSFVZYEBBoJ2RjZ0Hljuy6IZaUlUl
pfsKRUmZP4JTNJixkEm7nJu/Mxlot0l7JJOfXj06P/D3xvkBQH/lqoEC7WVvzqM0k/f4+Gat2nt7
ppqeCjCbiJX6HmN7vnca4LLc0BljH/yZ0ZejDQXGlA9GmYSthoumVw1wZ6PByxjrpxmeZq0hbMcD
XPCHGVB4hHCAkgUKrrNSulawelP...

```


To get un-encoded content, invoke it like this:
```
  curl -i -X GET https://ORGNAME-ENVNAME.apigee.net/base64-encoder/t1
```


## Building

Building from source requires Java 1.8, and Maven. 

1. unpack (if you can read this, you've already done that).

2. Before building _the first time_, configure the build on your machine by loading the Apigee jars into your local cache:
  ```
  ./buildsetup.sh
  ```

3. Build with maven.  
  ```
  mvn clean package
  ```
  This will build the jar and also run all the tests.



## Build Dependencies

- Apigee Edge expressions v1.0
- Apigee Edge message-flow v1.0
- Apache commons IO
- Apache commons Codec


## License

This material is Copyright 2017 Google Inc.
and is licensed under the [Apache 2.0 License](LICENSE). This includes the Java code as well as the API Proxy configuration. 

## Bugs

* None?
