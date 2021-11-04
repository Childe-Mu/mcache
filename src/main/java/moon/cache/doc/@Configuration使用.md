# @Configuration使用

## 前言

`@Configuration` 用于定义配置类，可替换XML配置文件，被注解的类内部包含一个或多个`@Bean`注解方法。可以被`AnnotationConfigApplicationContext`或者`AnnotationConfigWebApplicationContext` 进行扫描。用于构建bean定义以及初始化Spring容器。

## 实例

### @Configuration 加载Spring方法

Car.java

```cpp
public class Car {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
```

定义Config类

```java
@Configuration
public class Config {
    public Config() {
        System.out.println("TestConfig容器初始化...");
    }

    @Bean(name = "getMyCar")
    public Car getCar() {
        Car c = new Car();
        c.setName("dankun");
        return c;
    }
}
```

实例化

```java
public void testConfig() {
        ApplicationContext context = new AnnotationConfigApplicationContext(Config.class);
        Car car = (Car)context.getBean("car");
        System.out.println(car.getName());
    }
// 输出
// TestConfig容器初始化...
// dankun
```

### @Configuration + @Component

`@Configuration`也附带了@Component的功能。所以理论上也可以使用`@Autowared`功能。上述代码可以改成下面形式
Car.java

```java
@Component
public class Car {
    @Value("dankun")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
```

Config.java

``` java
@Configuration
@ComponentScan("com.wuyue.annotation")
public class Config {
    public Config() {
        System.out.println("TestConfig容器初始化...");
    }
}
```

测试主入口

```java
public class TestConfig {
    @Test
    public void testConfig() {
        ApplicationContext context = new AnnotationConfigApplicationContext(Config.class);
        Car car = (Car)context.getBean("car");
        System.out.println(car.getName());
    }
}
// 输出
// TestConfig容器初始化...
// dankun
```

## 总结

- @Configuation等价于`<Beans></Beans>`
- @Bean 等价于`<Bean></Bean>`
- @ComponentScan等价于`<context:component-scan base-package="com.dxz.demo"/>`
- @Component 等价于`<Bean></Bean>`

### @Bean VS @Component

- 两个注解的结果是相同的，bean都会被添加到Spring上下文中。
- @Component 标注的是类,允许通过自动扫描发现。@Bean需要在配置类`@Configuation`中使用。
- @Component类使用的方法或字段时不会使用`CGLIB`增强。而在@Configuration类中使用方法或字段时则使用CGLIB创造协作对象

假设我们需要将一些第三方的库组件装配到应用中或者 我们有一个在多个应用程序中共享的模块，它包含一些服务。并非所有应用都需要它们。

如果在这些服务类上使用@Component并在应用程序中使用组件扫描，我们最终可能会检测到超过必要的bean。导致应用程序无法启动，但是我们可以使用 `@Bean`来加载。

因此，基本上，使用`@Bean`将第三方类添加到上下文中。和`@Component`，如果它只在你的单个应用程序中