# 前言

慕课网上一个非常不错的分布式秒杀商城的课程，老师讲得非常棒，全程高能，干货满满，尊重知识产权，贴上[课程地址](https://coding.imooc.com/class/338.html)。

------

# 进阶项目核心知识点

![](https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/points.png)

------

# 基础项目回顾

## 项目结构—数据模型

![](https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/models.png)

![](https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/datamodels.png)

## 项目结构—DAO/Service/Controller结构

![](https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/classmodels.png)

------

## 全局异常处理类

在之前的基础项目中，抛出的`BizException`会被`BaseController`拦截到，并进行相应处理。但是如果前端发送到后端的URL找不到，即**404/405错误**，此时根本进入不了后端的`Controller`，需要处理。

新建一个`controller.GlobalExceptionHandler`的类，整个类加上`@ControllerAdvice`接口，表示这是一个**`AOP增强类`**，在什么时候增强呢？就需要我们编写。

```java
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public CommonReturnType doError(HttpServletRequest request, HttpServletResponse response,Exception ex){
        ex.printStackTrace();
        Map<String,Object> responseData=new HashMap<>();
        if(ex instanceof BizException){
            BizException bizException=(BizException)ex;
            responseData.put("errCode",bizException.getErrCode());
            responseData.put("errMsg",bizException.getErrMsg());
        }else if(ex instanceof ServletRequestBindingException){
            //@RequestParam是必传的，如果没传，就会触发这个异常
            responseData.put("errCode", EmBizError.UNKNOWN_ERROR.getErrCode());
            responseData.put("errMsg","URL绑定路由问题");
        }else if(ex instanceof NoHandlerFoundException){
            responseData.put("errCode", EmBizError.UNKNOWN_ERROR.getErrCode());
            responseData.put("errMsg","没有找到对应的访问路径");
        }else{
            responseData.put("errCode", EmBizError.UNKNOWN_ERROR.getErrCode());
            responseData.put("errMsg",EmBizError.UNKNOWN_ERROR.getErrMsg());
        }
        return CommonReturnType.create(responseData,"fail");
    }
}
```

最后还有一步，在Spring配置文件中添加：

```properties
#处理404/405
spring.mvc.throw-exception-if-no-handler-found=true
spring.resources.add-mappings=false
```

这样，404/405错误会触发`NoHandlerFoundException`，然后被`GlobalExceptionHandler`捕获到。

# 项目云端部署

## 数据库部署

使用`mysqldump -uroot -ppassword --databases dbName`指令，即可将开发环境的数据库dump成SQL语句。在云端服务器，直接用MySQL运行dump出来的SQL语句即可。

## 项目打包

本项目打成`jar`包，在服务器直接用`java -jar`运行。`maven`打`jar`包首先需要添加以下属性，以便在打包的时候知道JDK的位置，不然报错。

```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.source>1.7</maven.compiler.source>
    <maven.compiler.target>1.7</maven.compiler.target>
    <JAVA_HOME>C:/Program Files/Java/jdk1.8.0_201</JAVA_HOME>
</properties>
```

然后添加`spring-boot-maven-plugin`插件，使打包后的文件，能够找到Spring Boot的入口类，即`App.java`。

```xml
<plugins>
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <compilerArguments>
            <verbose />
            <bootclasspath>${JAVA_HOME}/jre/lib/rt.jar</bootclasspath>
          </compilerArguments>
          <source>1.8</source>
          <target>1.8</target>
        </configuration>
    </plugin>
    <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
    </plugin>
</plugins>
```

最后在开发目录执行`mvn clean package`即会清空`target`并打成`jar`包。

## deploy启动脚本

有的时候，线上环境需要**更改一些配置**，比如在`9090`端口部署等等。Spring Boot支持在线上环境中使用`spring.config.additional-location`指定线上环境的配置文件，而不是打到`jar`包里的配置文件。

新建一个`sh`文件：

```shell
nohup java -Xms400m -Xmx400m -XX:NewSize=200m -XX:MaxNewSize=200m -jar miaosha.jar --spring.config.additional-location=/usr/projects/application.properties
```

使用`./deploy.sh &`即可在后台启动，使用`tail -f nohup.out`即可查看项目启动、运行的信息。

# jmeter性能压测

本项目使用`jmeter`来进行并发压测。使用方法简单来说就是新建一个线程组，添加需要压测的接口地址，查看结果树和聚合报告。

# 单机服务器并发容量问题和优化

## 项目架构

![](https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/frame1.png)

## 发现并发容量问题

使用`pstree -p pid | wc -l`命令可以查看Java进程一共维护了多少个线程，在没有压测的时候，Tomcat维护了31个线程（不同机器该值不一定）。而进行压测的时候，Tomcat维护的线程数量猛增至200多个。

使用`top -H`命令可以查看CPU的使用情况，主要关注`us`，用户进程占用的CPU；`sy`，内核进程占用的CPU。还有`load average`，这个很重要，反映了CPU的负载强度。

在**当前线程数量**的情况下，发送100个线程，CPU的压力**不算太大**，所有请求都得到了处理；而发送**5000**个线程，大量请求报错，默认的线程数量不够用了，可见可以提高Tomcat维护的线程数。

## Spring Boot内嵌Tomcat线程优化

高并发条件下，就是要榨干服务器的性能，而Spring Boot内嵌Tomcat默认的线程设置比较“温柔”——默认**最大等待队列**为100，默认**最大可连接**数为10000，默认**最大工作线程**数为200，默认**最小工作线程**数为10。当请求超过200+100后，会拒绝处理；当连接超过10000后，会拒绝连接。对于最大连接数，一般默认的10000就行了，而其它三个配置，则需要根据需求进行优化。

在`application.properties`里面进行修改：

`server.tomcat.accept-count=1000`

`server.tomcat.max-threads=800`

`server.tomcat.min-spare-threads=100`

`server.tomcat.max-connections=10000`（默认）

这里**最大等待队列**设为1000，**最大工作线程**数设为800，**最小工作线程**数设为100。

**等待队列不是越大越好**，一是受到内存的限制，二是大量的出队入队操作耗费CPU性能。

**最大线程数不是越大越好**，因为线程越多，CPU上下文切换的开销越大，存在一个“阈值”，对于一个4核8G的服务器，经验值是800。

而最小线程数设为100，则是为了应付一些**突发情况**。

这样，当正常运行时，Tomcat维护了大概100个线程左右，而当压测时，线程数量猛增到800多个。

## Spring Boot内嵌Tomcat网络连接优化

当然Spring Boot并没有把内嵌Tomcat的所有配置都导出。一些配置需要通过` WebServerFactoryCustomizer<ConfigurableWebServerFactory>`接口来实现自定义。

这里需要自定义`KeepAlive`长连接的配置，减少客户端和服务器的连接请求次数，避免重复建立连接，提高性能。

```java
@Component
public class WebServerConfiguration implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {
    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        //使用对应工厂类提供给我们的接口，定制化Tomcat connector
        ((TomcatServletWebServerFactory) factory).addConnectorCustomizers(new TomcatConnectorCustomizer() {
            @Override
            public void customize(Connector connector) {
                Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
                //定制化KeepAlive Timeout为30秒
                protocol.setKeepAliveTimeout(30000);
                //10000个请求则自动断开
                protocol.setMaxKeepAliveRequests(10000);
            }
        });
    }
}
```

## 小结

这一节我们通过`pstree -p pid | wc -l`和`top -H`指令，配合`jmeter`压测工具：

1. 发现了Spring Boot内嵌Tomcat的**线程容量问题**。通过在Spring Boot配置文件中添加配置项，提高了Tomcat的等待队列长度、最大工作线程、最小工作线程，榨干服务器性能。
2. Spring Boot内嵌Tomcat默认使用`HTTP 1.0`的**短连接**，由于Spring Boot并没有把所有Tomcat配置都暴露出来，所以需要编写一个配置类使用`HTTP 1.1`的**长连接**。

## 接下来的优化方向

一是对服务器进行**分布式扩展**，二是**优化SQL查询**，比如添加索引。

# 分布式扩展优化

## 项目架构

![](https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/frame2.png)

## Nginx部署前端静态资源

用户通过`nginx/html/resources`访问前端静态页面。而Ajax请求则会通过nginx反向代理到两台不同的应用服务器。

**项目架构**：

![](https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/frame3.png)

将静态资源上传到相应目录，并修改`nginx.conf`中的

```text
location /resources/ {
	alias /usr/local/openresty/nginx/html/resources/; 
	index index.html index.html;
}
```

这样，用户就能通过```http://miaoshaserver/resources/```访问到静态页面。

## Nginx反向代理处理Ajax请求

Ajax请求通过Nginx反向代理到两台应用服务器，实现负载分担。在`nginx.conf`里面添加以下字段：

```text
upstream backend_server{
    server miaoshaApp1_ip weight=1;
    server miaoshaApp2_ip weight=1;
}
...
server{
    location / {
        proxy_pass http://backend_server;
        proxy_set_header Host $http_host:$proxy_port;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

这样，用`http://miaoshaserver`访问Nginx服务器，请求会被均衡地代理到下面的两个backend服务器上。

## 开启Tomcat Access Log验证

开启这个功能可以查看是哪个IP发过来的请求，在`application.properties`里面添加，非必须。

`server.tomcat.accesslog.enabled=true`

`server.tomcat.accesslog.directory=/opt/java/miaosha/tomcat`

`server.tomcat.accesslog.pattern=%h %l %u %t "%r" %s %b %D`

## Nginx反向代理长连接优化

Nginx服务器与**前端**的连接是**长连接**，但是与后端的**代理服务器**，默认是**短连接**，所以需要新添配置项。

```text
upstream backend_server{
    server miaoshaApp1_ip weight=1;
    server miaoshaApp2_ip weight=1;
    keepalive 30;
}
...
server{
    location / {
        proxy_pass http://backend_server;
        proxy_set_header Host $http_host:$proxy_port;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        
        proxy_http_version 1.1;
        proxy_set_header Connection "";
    }
}
```

## 分布式扩展后的效果

**单机环境**下，发送1000*30个请求，TPS在**1500**左右，平均响应时间**460**毫秒。CPU的`us`高达8.0，`loadaverage`三个指标加起来接近于2了（CPU核数）。

**分布式**扩展后，TPS在**1800**左右，平均响应时间**440**毫秒。但是CPU的`us`只有2.5左右，`loadaverage`1分钟在0.5左右，服务器的压力小了很多，有更多的并发提升空间。

**后端长连接后**，虽然TPS也是1800多，但是响应时间降低到了**350**毫秒。

（通过`netstat -an | grep miaoshaApp1_ip`可以查看Nginx服务器与后端服务器的连接情况，没开启长连接时，每次连接端口都在变，开启后，端口维持不变。）

## Nginx高性能原因—epoll多路复用

在了解**epoll多路复用**之前，先看看**Java BIO**模型，也就是Blocking IO，阻塞模型。当客户端与服务器建立连接之后，通过`Socket.write()`向服务器发送数据，只有当数据写完之后，才会发送。如果当Socket缓冲区满了，那就不得不阻塞等待。

接下来看看**Linux Select**模型。该模式下，会监听一定数量的客户端连接，一旦发现有变动，就会唤醒自己，然后遍历这些连接，看哪些连接发生了变化，执行IO操作。相比阻塞式的BIO，效率更高，但是也有个问题，如果10000个连接变动了1个，那么效率将会十分低。此外，**Java NIO**，即New IO或者Non-Blocking IO就借鉴了Linux Select模型。

而**epoll模型**，在**Linux Select**模型之上，新增了**回调函数**，一旦某个连接发生变化，直接执行回调函数，不用遍历，效率更高。

## Nginx高性能原因—master-worker进程模型

通过`ps -ef|grep nginx`命令可以看到有两个Nginx进程，一个标注为`master`，一个标注为`worker`，而且`worker`进程是`master`进程的子进程。这种父子关系的好处就是，`master`进程可以管理`worker`进程。

![](https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/ngxin2.jpg)

### Ngxin进程结构

![](https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/nginx.png)

### Master-worker高效原理

客户端的请求，并不会被`master`进程处理，而是交给下面的`worker`进程来处理，多个`worker`进程通过“**抢占**”的方式，取得处理权。如果某个`worker`挂了，`master`会立刻感知到，用一个新的`worker`代替。这就是Nginx高效率的原因之一，也是可以平滑重启的原理。

此外，`worker`进程是单线程的，没有阻塞的情况下，效率很高。而`epoll`模型避免了阻塞。

综上，**epoll机制**+**master-worker机制**使得`worker`进程可以高效率地执行单线程IO操作。

## Nginx高性能原因—协程机制

nginx引入了一种比线程更小的概念，那就是“**协程**”。协程依附于内存模型，切换开销更小；遇到阻塞，nginx会立刻剥夺执行权；由于在同一个线程内，不需要加锁。

## 小结

这一节对单机系统进行了分布式扩展，使得吞吐量和响应时间有了一定提升。虽然提升不大，但是单个服务器的压力明显降低。

1. 首先把前端资源部署到了Nginx服务器。
2. 然后把Nginx作为反向代理服务器，把后端项目部署到了另外两台服务器。
3. 接着优化了Nginx与后端服务器的连接。
4. 最后分析了Nginx高效的原因，包括epoll多路复用、master-worker机制、协程机制。

## 接下来的优化方向

之前的用户登录凭证，是放在`HttpSession`里面的，而`HttpSession`又存放在Tomcat服务器。一旦实现了分布式扩展，多台服务器无法共享同一个Session，所以就需要进入**分布式会话**。

------

# 分布式会话

## 基于Cookie传输SessionId

就是把Tomcat生成的`SessionId`转存到Redis服务器上，从而实现分布式会话。

在之前的项目引入两个`jar`包，分别是`spring-boot-starter-data-redis`和`spring-session-data-redis`，某些情况下，可能还需要引入`spring-security-web`。

`config`包下新建一个`RedisConfig`的类，暂时没有任何方法和属性，添加`@Component`和`@EnableRedisHttpSession(maxInactiveIntervalInSeconds=3600)`注解让Spring识别并自动配置过期时间。

接着在`application.properties`里面添加Redis相关连接配置。

```properties
spring.redis.host=RedisServerIp
spring.redis.port=6379
spring.redis.database=0
spring.redis.password=
```

**最后**！**最后**！**最后**，由于`UserModel`对象会被存到Redis里面，需要被**序列化**，所以要对`UserModel`类实现`Serializable`接口。

这样，之前的代码，就会自动将Session保存到Redis服务器上。

```java
this.httpServletRequest.getSession().setAttribute("IS_LOGIN",true);
this.httpServletRequest.getSession().setAttribute("LOGIN_USER",userModel);
```

## 基于Token传输类似SessionId

Spring Boot在Redis存入的`SessionId`有多项，不够简洁。一般常用UUID生成类似`SessionId`的唯一登录凭证`token`，然后将生成的`token`作为KEY，`UserModel`作为VALUE存入到Redis服务器。

```java
String uuidToken=UUID.randomUUID().toString();
uuidToken=uuidToken.replace("-","");
//建立Token与用户登录态的联系
redisTemplate.opsForValue().set(uuidToken,userModel);
//设置超时时间
redisTemplate.expire(uuidToken,1, TimeUnit.HOURS);
return CommonReturnType.create(uuidToken);
```

将生成的`token`返回给前端，前端在登录成功之后，将`token`**存放到`localStorage`里面**。

```javascript
if (data.status == "success") {
    alter("登录成功");
    var token = data.data;
    window.localStorage["token"] = token;
    window.location.href = "listitem.html";
}
```

前端的下单操作，需要验证登录状态。

```javascript
var token = window.localStorage["token"];
if (token == null) {
    alter("没有登录，不能下单");
    window.location.href = "login.html";
    return false;
}
```

在请求后端下单接口的时候，需要把这个`token`带上。

```javascript
$.ajax({
    type: "POST",
    url: "http://" + g_host + "/order/createorder?token=" + token,
    ···
});
```

后端之前是使用`SessionId`来获取登录状态的。

```java
Boolean isLogin=(Boolean)httpServletRequest.getSession().getAttribute("IS_LOGIN");
if(isLogin==null||!isLogin.booleanValue())
    throw new BizException(EmBizError.USER_NOT_LOGIN,"用户还未登录，不能下单");
UserModel userModel = (UserModel)httpServletRequest.getSession().getAttribute("LOGIN_USER");
```

现在利用前端传过来的`token`，从Redis服务器里面获取这个`token`对应的`UserModel`对象。

```java
String token=httpServletRequest.getParameterMap().get("token")[0];
if(StringUtils.isEmpty(token)){
    throw new BizException(EmBizError.USER_NOT_LOGIN,"用户还未登录，不能下单");
}
UserModel userModel= (UserModel) redisTemplate.opsForValue().get(token);
if(userModel==null){
    throw new BizException(EmBizError.USER_NOT_LOGIN,"登录过期，请重新登录");
}
```

## 小结

本节引入了分布式会话，有两种常见的实现方式：

1. 第一种是通过Spring提供的API，将Tomcat的`SessionId`和`UserModel`存到Redis服务器上。
2. 第二种是通过UUID生成登录`token`，将`token`和`UserModel`存到Redis服务器上。

## 接下来的优化方向

目前服务器的性能瓶颈在于数据库的大量读取操作，接下来会引入**缓存**，优化查询。

# 查询优化之多级缓存

多级缓存有两层含义，一个是缓存，一个是多级。所谓缓存，