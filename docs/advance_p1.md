[【基础项目笔记】](https://github.com/MaJesTySA/miaosha_Shop/blob/master/docs/base.md)

[【进阶项目笔记 下】](https://github.com/MaJesTySA/miaosha_Shop/blob/master/docs/advance_p2.md)

------

* [进阶项目核心知识点](#进阶项目核心知识点)
* [基础项目回顾](#基础项目回顾)
  * [项目结构—数据模型](#项目结构数据模型)
  * [项目结构—DAO/Service/Controller结构](#项目结构daoservicecontroller结构)
  * [全局异常处理类](#全局异常处理类)
* [项目云端部署](#项目云端部署)
  * [数据库部署](#数据库部署)
  * [项目打包](#项目打包)
  * [deploy启动脚本](#deploy启动脚本)
* [jmeter性能压测](#jmeter性能压测)
* [单机服务器并发容量问题和优化](#单机服务器并发容量问题和优化)
  * [项目架构](#项目架构)
  * [发现并发容量问题](#发现并发容量问题)
  * [Spring Boot内嵌Tomcat线程优化](#spring-boot内嵌tomcat线程优化)
  * [Spring Boot内嵌Tomcat网络连接优化](#spring-boot内嵌tomcat网络连接优化)
  * [小结](#小结)
  * [优化后的效果](#优化后的效果)
  * [接下来的优化方向](#接下来的优化方向)
* [分布式扩展优化](#分布式扩展优化)
  * [项目架构](#项目架构-1)
  * [Nginx部署前端静态资源](#nginx部署前端静态资源)
  * [Nginx反向代理处理Ajax请求](#nginx反向代理处理ajax请求)
  * [开启Tomcat Access Log验证](#开启tomcat-access-log验证)
  * [Nginx反向代理长连接优化](#nginx反向代理长连接优化)
  * [分布式扩展后的效果](#分布式扩展后的效果)
  * [Nginx高性能原因—epoll多路复用](#nginx高性能原因epoll多路复用)
  * [Nginx高性能原因—master-worker进程模型](#nginx高性能原因master-worker进程模型)
    * [Ngxin进程结构](#ngxin进程结构)
    * [Master-worker高效原理](#master-worker高效原理)
  * [Nginx高性能原因—协程机制](#nginx高性能原因协程机制)
  * [小结](#小结-1)
  * [接下来的优化方向](#接下来的优化方向-1)
* [分布式会话](#分布式会话)
  * [基于Cookie传输SessionId](#基于cookie传输sessionid)
  * [基于Token传输类似SessionId](#基于token传输类似sessionid)
  * [小结](#小结-2)
  * [接下来的优化方向](#接下来的优化方向-2)
* [查询优化之多级缓存](#查询优化之多级缓存)
  * [项目架构](#项目架构-2)
  * [优化商品查询接口—单机版Redis缓存](#优化商品查询接口单机版redis缓存)
    * [序列化格式问题](#序列化格式问题)
    * [时间序列化格式问题](#时间序列化格式问题)
  * [优化商品查询接口—本地热点缓存](#优化商品查询接口本地热点缓存)
    * [本地缓存缺点](#本地缓存缺点)
  * [缓存优化后的效果](#缓存优化后的效果)
  * [Nginx Proxy Cache缓存](#nginx-proxy-cache缓存)
    * [Nginx Proxy Cache缓存效果](#nginx-proxy-cache缓存效果)
  * [Nginx lua脚本](#nginx-lua脚本)
    * [lua脚本实战](#lua脚本实战)
  * [OpenResty—Shared dict](#openrestyshared-dict)
    * [Shared dict缓存效果](#shared-dict缓存效果)
  * [小结](#小结-3)
  * [接下来的优化方向](#接下来的优化方向-3)
* [查询优化之页面静态化](#查询优化之页面静态化)
  * [项目架构](#项目架构-3)
  * [CDN](#cdn)
    * [CDN使用](#cdn使用)
    * [CDN优化效果](#cdn优化效果)
    * [CDN深入—cache controll响应头](#cdn深入cache-controll响应头)
      * [选择缓存策略](#选择缓存策略)
      * [有效性验证](#有效性验证)
      * [请求资源流程](#请求资源流程)
    * [CDN深入—浏览器三种刷新方式](#cdn深入浏览器三种刷新方式)
      * [a标签/回车刷新](#a标签回车刷新)
      * [F5刷新](#f5刷新)
      * [CTRL+F5强制刷新](#ctrlf5强制刷新)
    * [CDN深入—自定义缓存策略](#cdn深入自定义缓存策略)
    * [CDN深入—静态资源部署策略](#cdn深入静态资源部署策略)
      * [部署窘境](#部署窘境)
      * [解决方法](#解决方法)
  * [全页面静态化](#全页面静态化)
    * [phantomJS实现全页面静态化](#phantomjs实现全页面静态化)
  * [小结](#小结-4)
  * [接下来的优化方向](#接下来的优化方向-4)
* [优化效果总结](#优化效果总结)
  * [Tomcat优化](#Tomcat优化)
  * [分布式扩展优化](#分布式扩展优化-1)
  * [缓存优化](#缓存优化)
  * [CDN优化](#CDN优化)

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

## 全局异常处理类

在之前的基础项目中，抛出的`BizException`会被`BaseController`拦截到，并进行相应处理。但是如果前端发送到后端的URL找不到，即**404/405错误**，此时根本进入不了后端的`Controller`，需要处理。

新建一个`controller.GlobalExceptionHandler`的类，整个类加上`@ControllerAdvice`接口，表示这是一个**AOP增强类**，在什么时候增强呢？就需要我们编写。

```java
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public CommonReturnType doError(HttpServletRequest request, HttpServletResponse response,Exception ex){
        //开发过程中使用 ex.printStackTrace();
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

------

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

------

# jmeter性能压测

本项目使用`jmeter`来进行并发压测。使用方法简单来说就是新建一个线程组，添加需要压测的接口地址，查看结果树和聚合报告。

------

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

## 优化后的效果

未调整线程数之前（2核CPU），200*50个请求，TPS在**150**左右，平均响应**1000毫秒**。调整之后，TPS在**250**左右，平均响应**400**毫秒。

## 接下来的优化方向

一是对服务器进行**分布式扩展**，二是**优化SQL查询**，比如添加索引。

------

# 分布式扩展优化

## 项目架构

![](https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/frame2.png)

## Nginx部署前端静态资源

用户通过`nginx/html/resources`访问前端静态页面。而Ajax请求则会通过Nginx反向代理到两台不同的应用服务器。

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

- **单机环境**下，发送1000*30个请求，TPS在**1400**左右，平均响应时间**460**毫秒。CPU的`us`高达8.0，`loadaverage`三个指标加起来接近于2了（CPU核数）。
- **分布式**扩展后，TPS在**1700**左右，平均响应时间**440**毫秒。但是CPU的`us`只有2.5左右，`loadaverage`1分钟在0.5左右，服务器的压力小了很多，有更多的并发提升空间。
- **后端长连接后**，虽然TPS也是1700多，但是响应时间降低到了**350**毫秒。

通过`netstat -an | grep miaoshaApp1_ip`可以查看Nginx服务器与后端服务器的连接情况，没开启长连接时，每次连接端口都在变，开启后，端口维持不变。

## Nginx高性能原因—epoll多路复用

在了解**epoll多路复用**之前，先看看**Java BIO**模型，也就是Blocking IO，阻塞模型。当客户端与服务器建立连接之后，通过`Socket.write()`向服务器发送数据，只有当数据写完之后，才会发送。如果当Socket缓冲区满了，那就不得不阻塞等待。

接下来看看**Linux Select**模型。该模式下，会监听一定数量的客户端连接，一旦发现有变动，就会唤醒自己，然后遍历这些连接，看哪些连接发生了变化，执行IO操作。相比阻塞式的BIO，效率更高，但是也有个问题，如果10000个连接变动了1个，那么效率将会十分低下。此外，**Java NIO**，即New IO或者Non-Blocking IO就借鉴了Linux Select模型。

而**epoll模型**，在**Linux Select**模型之上，新增了**回调函数**，一旦某个连接发生变化，直接执行回调函数，不用遍历，效率更高。

## Nginx高性能原因—master-worker进程模型

通过`ps -ef|grep nginx`命令可以看到有两个Nginx进程，一个标注为`master`，一个标注为`worker`，而且`worker`进程是`master`进程的子进程。这种父子关系的好处就是，`master`进程可以管理`worker`进程。

![](https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/ngxin2.jpg)

### Ngxin进程结构

![](https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/nginx.png)

### Master-worker高效原理

客户端的请求，并不会被`master`进程处理，而是交给下面的`worker`进程来处理，多个`worker`进程通过“**抢占**”的方式，取得处理权。如果某个`worker`挂了，`master`会立刻感知到，用一个新的`worker`代替。这就是Nginx高效率的原因之一，也是可以平滑重启的原理。

此外，`worker`进程是单线程的，没有阻塞的情况下，效率很高。而epoll模型避免了阻塞。

综上，**epoll机制**+**master-worker机制**使得`worker`进程可以高效率地执行单线程I/O操作。

## Nginx高性能原因—协程机制

Nginx引入了一种比线程更小的概念，那就是“**协程**”。协程依附于内存模型，切换开销更小；遇到阻塞，Nginx会立刻剥夺执行权；由于在同一个线程内，也不需要加锁。

## 小结

这一节对单机系统进行了分布式扩展，使得吞吐量和响应时间都有了一定提升。虽然提升不大，但是单个服务器的压力明显降低。

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

------

# 查询优化之多级缓存

多级缓存有两层含义，一个是**缓存**，一个是**多级**。我们知道，内存的速度是磁盘的成百上千倍，高并发下，从磁盘I/O十分影响性能。所谓缓存，就是将磁盘中的热点数据，暂时存到内存里面，以后查询直接从内存中读取，减少磁盘I/O，提高速度。所谓多级，就是在多个层次设置缓存，一个层次没有就去另一个层次查询。

## 项目架构

![](https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/frame4.png)

## 优化商品查询接口—单机版Redis缓存

之前的`ItemController.getItem`接口，来一个`Id`，就调用`ItemService`去数据库查询一次。`ItemService`会查三张表，分别是商品信息表`item`表、商品库存`stock`表和活动信息表`promo`，十分影响性能。

所以修改`ItemController.getItem`接口，思路很简单，先从Redis服务器获取，若没有，则从数据库查询并存到Redis服务。有的话直接用。

```java
@RequestMapping(value = "/get",method = {RequestMethod.GET})
@ResponseBody
public CommonReturnType getItem(@RequestParam(name = "id")Integer id){
    ItemModel itemModel=(ItemModel)redisTemplate.opsForValue().get("item_"+id);
    //如果不存在，就执行下游操作，到数据查询
    if(itemModel==null){
        itemModel=itemService.getItemById(id);
        //设置itemModel到redis服务器
        redisTemplate.opsForValue().set("item_"+id,itemModel);
        //设置失效时间
        redisTemplate.expire("item_"+id,10, TimeUnit.MINUTES);
    }
    ItemVO itemVO=convertVOFromModel(itemModel);
    return CommonReturnType.create(itemVO);
}
```

### 序列化格式问题

采用上述方式，存到Redis里面的VALUE是类似`/x05/x32`的二进制格式，我们需要自定义`RedisTemplate`的序列化格式。

之前我们在`config`包下面创建了一个`RedisConfig`类，里面没有任何方法，接下来我们编写一个方法。

```java
@Bean
public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory){
    RedisTemplate redisTemplate=new RedisTemplate();
    redisTemplate.setConnectionFactory(redisConnectionFactory);
    
    //首先解决key的序列化格式
    StringRedisSerializer stringRedisSerializer=new StringRedisSerializer();
    redisTemplate.setKeySerializer(stringRedisSerializer);
    
    //解决value的序列化格式
    Jackson2JsonRedisSerializer jackson2JsonRedisSerializer=new Jackson2JsonRedisSerializer(Object.class);
    redisTemplate.setValueSerializer(jackson2JsonRedisSerializer);
    
    return redisTemplate;
}
```

这样，`ItemModel`的内容就会以JSON的格式存储和显示。

### 时间序列化格式问题

但是这样对于日期而言，序列化后是一个很长的毫秒数。我们希望是`yyyy-MM-dd HH:mm:ss`的格式，还需要进一步处理。新建`serializer`包，里面新建两个类。

```java
public class JodaDateTimeJSONSerializer extends JsonSerializer<DateTime> {
    @Override
    public void serialize(DateTime dateTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeString(dateTime.toString("yyyy-MM-dd HH:mm:ss"));
    }
}
```

```java
public class JodaDateTimeDeserializer extends JsonDeserializer<DateTime> {
    @Override
    public DateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        String dateString = jsonParser.readValueAs(String.class);
        DateTimeFormatter formatter=DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        return DateTime.parse(dateString,formatter);
    }
}
```

回到`RedisConfig`类里面：

```java
public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory){
    ···
    //解决日期的序列化格式
    ObjectMapper objectMapper=new ObjectMapper();
    SimpleModule simpleModule=new SimpleModule();
    simpleModule.addSerializer(DateTime.class,new JodaDateTimeJSONSerializer());
    simpleModule.addDeserializer(DateTime.class,new JodaDateTimeDeserializer());
    objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
    objectMapper.registerModule(simpleModule);
    jackson2JsonRedisSerializer.setObjectMapper(objectMapper);
    redisTemplate.setValueSerializer(jackson2JsonRedisSerializer);
    return redisTemplate;
}
```

这样就规范了时间类型的序列化格式。

## 优化商品查询接口—本地热点缓存

Redis缓存虽好，但是有网络I/O，没有本地缓存快。我们可以在Redis的前面再添加一层“**本地热点**”缓存。所谓**本地**，就是利用**本地JVM的内存**。所谓**热点**，由于JVM内存有限，仅存放**多次查询**的数据。

本地缓存，说白了就是一个`HashMap`，但是`HashMap`不支持并发读写，肯定是不行的。`j.u.c`包里面的`ConcurrentHashMap`虽然也能用，但是无法高效处理过期时限、没有淘汰机制等问题，所以这里使用了`Google`的`Guava Cache`方案。

`Guava Cache`除了线程安全外，还可以控制超时时间，提供淘汰机制。

引用`google.guava`包后，在`service`包下新建一个`CacheService`类。

```java
@Service
public class CacheServiceImpl implements CacheService {
    private Cache<String,Object> commonCache=null;
    @PostConstruct
    public void init(){
        commonCache= CacheBuilder.newBuilder()
                //初始容量
                .initialCapacity(10)
                //最大100个KEY，超过后会按照LRU策略移除
                .maximumSize(100)
                //设置写缓存后多少秒过期，还有根据访问过期即expireAfterAccess
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void setCommonCache(String key, Object value) {
       commonCache.put(key,value);
    }

    @Override
    public Object getFromCommonCache(String key) {
        return commonCache.getIfPresent(key);
    }
}
```

在`ItemController`里面，首先从本地缓存中获取，如果本地缓存没有，就去Redis里面获取，如果Redis也没有，就去数据库查询并存放到Redis里面。如果Redis里面有，将其获取后存到本地缓存里面。

```java
public CommonReturnType getItem(@RequestParam(name = "id")Integer id){
    ItemModel itemModel=null;
    //第一级：先去本地缓存
    itemModel=(ItemModel)cacheService.getFromCommonCache("item_"+id);
    if(itemModel==null){
        //第二级：从redis里面获取
        itemModel=(ItemModel)redisTemplate.opsForValue().get("item_"+id);
        //如果不存在，就执行下游操作，到数据查询
        if(itemModel==null){
            itemModel=itemService.getItemById(id);
            //设置itemModel到redis服务器
            redisTemplate.opsForValue().set("item_"+id,itemModel);
            //设置失效时间
            redisTemplate.expire("item_"+id,10, TimeUnit.MINUTES);
        }
        //填充本地缓冲
        cacheService.setCommonCache("item_"+id,itemModel);
    }
    ItemVO itemVO=convertVOFromModel(itemModel);
    return CommonReturnType.create(itemVO);
}
```

### 本地缓存缺点

本地缓存虽快，但是也有缺点：

1. 更新麻烦，容易产生脏缓存。
2. 受到JVM容量的限制。

## 缓存优化后的效果

- 之前进行分布式扩展后，发送1000*20个请求，TPS在**1700**左右，平均响应时间**350**毫秒左右。
- 引入Redis缓存后，TPS峰值达到了**2100**左右，平均响应时间**250**毫秒左右，Redis服务器压力不大，还可以继续加并发量。
- 引入本地缓存后，发现提升**十分巨大**，TPS峰值高达**3600**多，平均响应时间只有**50**毫秒左右。

再次压测1000*40个请求，发现TPS峰值高达**4100**多，平均响应时间在**145**毫秒左右。Redis服务器压力更小了，因为都被本地缓存拦截了。

## Nginx Proxy Cache缓存

通过Redis缓存，避免了MySQL大量的重复查询，提高了部分效率；通过本地缓存，减少了与Redis服务器的网络I/O，提高了大量效率。但实际上，前端（客户端）请求Nginx服务器，Nginx有分发过程，需要去请求后面的两台应用服务器，有一定网络I/O，能不能直接把**热点数据存放到Nginx服务器上**呢？答案是可以的。

Nginx Proxy Cache的原理是基于**文件系统**的，它把后端返回的响应内容，作为**文件**存放在Nginx指定目录下。

在`nginx.conf`里面配置`proxy cache`：

```text
upstream backend_server{
    server miaoshaApp1_ip weight=1;
    server miaoshaApp2_ip weight=1;
}
#申明一个cache缓存节点 evels 表示以二级目录存放
    proxy_cache_path /usr/local/openresty/nginx/tmp_cache levels=1:2 keys_zone=tmp_cache:100m inactive=7d max_size=10g;
...
server{
    location / {
        ···
        #proxy_cache 目录
        proxy_cache tmp_cache;
        proxy_cache_key $uri;
        #只有后端返回以下状态码才缓存
        proxy_cache_valid 200 206 304 302 7d;
    }
}
```

这样，当多次访问后端商品详情接口时，在`nginx/tmp_cache/dir1/dir2`下生成了一个**文件**。`cat`这个文件，发现就是JSON格式的数据。

### Nginx Proxy Cache缓存效果

发现TPS峰值只有**2800**左右，平均响应时间**225**毫秒左右，**不升反降**，这是为什么呢？原因就是，虽然用户可以直接从Nginx服务器拿到缓存的数据，但是这些数据是基于**文件系统**的，是存放在**磁盘**上的，有**磁盘I/O**，虽然减少了一定的网络I/O，但是磁盘I/O并没有内存快，得不偿失，所以不建议使用。

## Nginx lua脚本

那Nginx有没有一种基于“内存”的缓存策略呢？答案也是有的，可以使用Nginx lua脚本来做缓存。

lua也是基于协程机制的。

- 依附于线程的内存模型，切换开销小。
- 遇到阻塞则释放执行权，代码同步。
- 无需加锁。

lua脚本可以挂载在Nginx处理请求的起始、worker进程启动、内容输出等阶段。

### lua脚本实战

在OpenResty下新建一个lua文件夹，专门用来存放lua脚本。新建一个`init.lua`。

```lua
ngx.log(ngx.ERR, "init lua success");
```

在`nginx.conf`里面添加一个`init_by_lua_file`的字段，指定上述lua脚本的位置。这样，当Nginx启动的时候，就会执行这个lua脚本，输出"init lua success"。

------

当然，在Nginx启动的时候，挂载lua脚本并没有什么作用。一般在内容输出阶段，挂载lua脚本。

新建一个`staticitem.lua`，用`ngx.say()`输出一段字符串。在`nginx.conf`里面添加一个新的location：

```text
location /staticitem/get{
    default_type "text/html";
    content_by_lua_file ../lua/staticitem.lua;
}
```

访问`/staticitem/get`，在页面就会响应出`staticitem.lua`的内容。

------

新建一个`helloworld.lua`，使用`ngx.exec("/item/get?id=1")`访问某个URL。同样在`nginx.conf`里面添加一个`helloworld`location。这样，当访问`/helloworld`的时候就会跳转到`item/get?id=1`这个URL上。

## OpenResty—Shared dict

OpenResty对Nginx进行了扩展，添加了很多功能，比如集成了lua开发环境、提供了对MySQL、Redis、Memcached的支持等。比原版Nginx使用起来更加方便。

OpenResty的Shared dict是一种类似于`HashMap`的Key-Value**内存**结构，对所有`worker`进程可见，并且可以指定LRU淘汰规则。

和配置`proxy cache`一样，我们需要指定一个名为`my_cache`，大小为128m的`lua_shared_dict`：

```text
upstream backend_server
···
lua_shared_dict my_cahce 128m;
```

在lua文件夹下，新建一个`itemshareddict.lua`脚本，编写两个函数。

```lua
function get_from_cache(key)
    --类似于拿到缓存对象
    local cache_ngx = ngx.shared.my_cache
    --从缓存对象中，根据key获得值
    local value = cache_ngx.get(key)
    return value
end

function set_to_cache(key,value,exptime)
    if not exptime then 
        exptime = 0
    end
    local cache_ngx = ngx.shared.my_cache
    local succ,err,forcible = cache_ngx.set(key,value,exptime)
    return succ 
end
```

然后编写“main"函数：

```lua
--得到请求的参数，类似Servlet的request.getParameters
local args = ngx.req.get_uri_args()
local id = args["id"]
--从缓存里面获取商品信息
local item_model = get_from_cache("item_"..id)
if item_model == nil then
    --如果取不到，就请求后端接口
    local resp = ngx.location.capture("/item/get?id="..id)
    --将后端返回的json响应，存到缓存里面
    item_model = resp.body
    set_to_cache("item_"..id,item_model,1*60)
end
ngx.say(item_model)
```

新建一个`luaitem/get`的location，注意`default_type`是json。

```text
location /luaitem/get{
    default_type "application/json";
    content_by_lua_file ../lua/itemshareddict.lua;
}
```

### Shared dict缓存效果

压测`/luaitem/get`，峰值TPS在**4000**左右，平均响应时间**150ms**左右，比`proxy cache`要高出不少，跟使用两层缓存效果差不多。

使用Ngxin的Shared dict，**把压力转移到了Nginx服务器**，**后面两个Tomcat服务器压力减小**。同时**减少了与后面两个Tomcat服务器、Redis服务器和数据库服务器的网络I/O**，当网络I/O成为瓶颈时，Shared dict不失为一种好方法。

最后，Shared dict依然受制于缓存容量和缓存更新问题。

## 小结

本节首先使用**Redis**对商品详情信息进行了缓存。然后使用本地缓存**guava**在Redis之前再做一层缓存。随后，本节尝试了将缓存提前，提到离客户端更近的Nginx服务器上，减少网络I/O，开启了Nginx的**proxy cache**，但是由于proxy cache是基于文件系统的，有磁盘I/O，效果得不偿失。最后，本节使用**OpenResty Shared Dict+Nginx+Lua**将Nginx的缓存从磁盘提到内存，提升了性能。

## 接下来的优化方向

现在的架构，**前端资源每次都要进行请求**，能不能像缓存数据库数据一样，对**前端资源进行缓存呢**？答案也是可以的，下一章将讲解静态资源CDN，将页面静态化。

------

# 查询优化之页面静态化

## 项目架构

之前静态资源是直接从Nginx服务器上获取，而现在会先去CDN服务器上获取，如果没有则回源到Nginx服务器上获取。

![](https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/cdn.png)

## CDN

CDN是内容分发网络，一般用来存储（缓存）项目的静态资源。访问静态资源，会从离用户**最近**的CDN服务器上返回静态资源。如果该CDN服务器上没有静态资源，则会执行**回源**操作，从Nginx服务器上获取静态资源。

### CDN使用

1. 购买一个CDN服务器，选择要加速的域名（比如`miaoshaserver.jiasu.com`），同时要填写**源站**IP，也就是Nginx服务器，便于回源操作。
2. 接下来要配置`miaoshaserver.jiasu.com`的DNS解析规则。一般的解析规则是`A记录类型`，也就是把一个域名直接解析成**IP地址**。这里使用`CNAME`进行解析，将一个域名解析到另外一个域名。而这个”另一个域名“是云服务器厂商提供的，它会把请求解析到相应的CDN服务器上。
3. 访问`miaoshaserver.jiasu.com/resources/getitem.html?id=1`即可以CDN的方式访问静态资源。

### CDN优化效果

- 没有使用CDN优化：发送500*20个请求，TPS在**700**左右，平均响应时间**400ms**。
- 使用了CDN优化：TPS在**1300**左右，平均响应时间**150ms**，可见优化效果还是很好的。

### CDN深入—cache controll响应头

在响应里面有一个`cache controll`响应头，这个响应头表示**客户端是否可以缓存响应**。有以下几种缓存策略：

| 策略        | 说明                                                   |
| ----------- | ------------------------------------------------------ |
| private     | 客户端可以缓存                                         |
| public      | 客户端和代理服务器都可以缓存                           |
| max-age=xxx | 缓存的内容将在xxx秒后失效                              |
| no-cache    | 也会缓存，但是使用缓存之前会询问服务器，该缓存是否可用 |
| no-store    | 不缓存任何响应内容                                     |

#### 选择缓存策略

如果不缓存，那就选择`no-store`。如果需要缓存，但是需要重新验证，则选择`no-cache`；如果不需要重新验证，则选择`private`或者`public`。然后设置`max-age`，最后添加`ETag Header`。

<img src="https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/choosehead.png" width=60% />

#### 有效性验证

**ETag**：第一次请求资源的时候，服务器会根据**资源内容**生成一个**唯一标示ETag**，并返回给浏览器。浏览器下一次请求，会把**ETag**（If-None-Match）发送给服务器，与服务器的ETag进行对比。如果一致，就返回一个**304**响应，即**Not Modify**，**表示浏览器缓存的资源文件依然是可用的**，直接使用就行了，不用重新请求。

#### 请求资源流程

<img src="https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/requestResrProcess.png" width=80% />

### CDN深入—浏览器三种刷新方式

#### a标签/回车刷新

查看`max-age`是否有效，有效直接从缓存中获取，无效进入缓存协商逻辑。

#### F5刷新

取消`max-age`或者将其设置为0，直接进入缓存协商逻辑。

#### CTRL+F5强制刷新

直接去掉`cache-control`和协商头，重新请求资源。

### CDN深入—自定义缓存策略

CDN服务器，既充当了浏览器的服务端，又充当了Nginx的客户端。所以它的缓存策略尤其虫咬。除了按照服务器的`max-age`，CDN服务器还可以自己设置过期时间。

**总的规则就是**：源站没有配置，遵从CDN控制台的配置；CDN控制台没有配置，遵从服务器提供商的默认配置。源站有配置，CDN控制台有配置，遵从CDN控制台的配置；CDN控制台没有配置，遵从源站配置。

### CDN深入—静态资源部署策略

#### 部署窘境

假如服务器端的静态资源更新了，但是由于客户端的`max-age`还未失效，用的还是老的资源，文件名又一样，用户不得不使用CTRL+F5强制刷新，才能请求更新的静态资源。

#### 解决方法

1. **版本号**：在静态资源文件后面追加一个版本号，比如`a.js?v=1.0`。这种方法维护起来十分麻烦，比如只有一个`js`文件做了修改，那其它`html`、`css`文件要不要追加版本号呢？
2. **摘要**：对静态资源的内容进行哈希操作，得到一个摘要，比如`a.js?v=45edw`，维护起来更加方法。但是会导致是先部署`js`还是先部署`html`的问题。比如先部署`js`，那么`html`页面引用的还是老的`js`，`js`直接失效；如果先部署`html`，那么引用的`js`又是老版本的`js`。
3. **摘要作为文件名**：比如`45edw.js`，会同时存在新老两个版本，方便回滚。

## 全页面静态化

现在的架构是，用户通过CDN请求到了静态资源，然后静态页面会在加载的时候，发送一个Ajax请求到后端，接收到后端的响应后，再用**DOM渲染**。也就是每一个用户请求，都有一个**请求后端接口**并**渲染**的过程。那能不能取消这个过程，直接在服务器端把页面渲染好，返回一个纯`html`文件给客户端呢？

```javascript
//页面加载完成
jQuery(document).ready(function(){
    //请求后端接口
    $.ajax({
        ···
        //接受到响应
        success: function(){
        //根据响应填充标签
        reloadDom();
    }
    })
})
```

### phantomJS实现全页面静态化

phantomJS就像一个爬虫，会把页面中的JS执行完毕后，返回一个渲染完成的`html`文件。

```javascript
//引入包
var page = require("webpage").create();
var fs = require("fs");
page.open("http://miaoshaserver/resources/getitem.html?id=2",function(status){
    setTimeout(function(){
        fs.write("getitem.html",page.content,"w");
        phantom.exit();
    },1000);
})
```

打开`getitem.hmtl`，发现里面的标签都正确填充了，但是**还是发送了一次Ajax请求**，这不是我们想看到的。原因就在于，就算页面渲染完毕，Ajax请求的代码块仍然存在，仍然会发送。

在页面中添加一个隐藏域：`<input type="hidden" id="isInit" value="0"/>`。

新增`hasInit`、`setHasInit`、`initView`三个函数。

```javascript
function hasInit() {
    var isInit = $("#isInit").val();
    return isInit;
}

function setHasInit() {
    $("#isInit").val("1");
}

function initView() {
    var isInit = hasInit();
    //如果渲染过，直接返回
    if (isInit == "1") return;
    //否则发送ajax请求
    $.ajax({
        ···
        success: function (data) {
            if (data.status == "success") {
                global_itemVO = data.data;
                //渲染页面
                reloadDom();
                setInterval(reloadDom, 1000);
                //将isInit的值设为1
                setHasInit();
            } 
        ···
}
```

修改phantomJS代码。

```javascript
var page = require("webpage").create();
var fs = require("fs");
page.open("http://miaoshaserver/resources/getitem.html?id=2",function(status){
    //每隔1秒就尝试一次，防止JS没加载完
    var isInit = "0";
    setInterval(function(){
        if(isInit != "1"){
            //手动执行一次initView
            page.evaluate(function(){
                initView();
            })
            //手动设置hasInit
            isInit = page.evaluate(function(){
                return hasInit();
            })
        } else {
            fs.write("getitem.html",page.content,"w");
            phantom.exit();
        }
    },1000);
})
```

这样，当页面第一次加载时，`hasInit=0`，那么会发送Ajax请求并渲染页面，渲染完毕后，将`hasInit`置为1。当页面第二次加载时，由于`hasInit=1`，不会再次发送Ajax请求页面。

## 小结

这一章我们

1. 首先使用**CDN技术**将静态资源部署到CDN服务器上，提高了静态资源的响应速度。
2. 然后我们使用**全页面静态化技术**，使得用户在请求页面的时候，不会每次都去请求后端接口，然后进行页面渲染。而是直接得到一个已经渲染好的HTML页面，提高了响应速度。

## 接下来的优化方向

接下里我们会对**交易下单接口**进行性能优化，包括缓存库存、异步扣减库存等。

------

# 优化效果总结

## Tomcat优化

| 优化线程数和连接（200*50） | TPS  | 平均响应时间/ms |
| -------------------------- | ---- | --------------- |
| 优化前                     | 150  | 1000            |
| 优化后                     | 250  | 400             |

## 分布式扩展优化

| 分布式扩展（1000*30） | TPS  | 平均响应时间/ms | us   | load_average  |
| --------------------- | ---- | --------------- | ---- | ------------- |
| 单机环境              | 1400 | 460             | 8.0  | 三项相加≈核数 |
| 分布式扩展            | 1700 | 440             | 2.5  | 1分钟0.5      |
| 长连接优化            | 1700 | 350             | -    | -             |

## 缓存优化

| 缓存优化（1000*20）       | TPS      | 平均响应时间/ms |
| ------------------------- | -------- | --------------- |
| 未引入缓存                | 1700     | 350             |
| 引入Redis缓存             | 2100     | 250             |
| 引入本地缓存              | **3600** | **50**          |
| 引入Nginx Proxy Cache     | 2800     | 225             |
| 引入OpenResty Shared Dict | **4000** | 150             |

## CDN优化

| CDN优化（500*20） | TPS  | 平均响应时间/ms |
| ----------------- | ---- | --------------- |
| 未使用CDN         | 700  | 400             |
| 使用CDN           | 1300 | 150             |

------

[【进阶项目笔记 下】](https://github.com/MaJesTySA/miaosha_Shop/blob/master/docs/advance_p2.md)，包含**交易性能优化之缓存库存**，**交易性能优化之事务型消息**，**流量削峰技术**和**防刷限流技术**。

