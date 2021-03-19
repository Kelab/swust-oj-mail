> 本文作者：可乐可乐可，作者个人主页：[可乐可乐可的个人主页](https://juejin.cn/user/2788017220631854)

# 因为经费问题不得不使用限流降级队列

> 亲儿子（学校自研的新系统）在进行细致的需求分析时，竟然有经费问题，导致我们使用的邮箱可能挂掉或者被限流，第n次因为经费问题流下泪水233。
>
> 不过这也是自己的契机，当初用这个小轮子入了面试官的法眼（感谢面试官给机会，蟹蟹蟹蟹，需要字节跳动内推的可以私聊我哦）

![img](https://jirath.cn/PicGo/20210319223134.jpg)

（想亲自体验被QQ邮箱封号吗，不会很严重，停一会儿就恢复了，下面是Spring Boot 的一个测试类，可以用他体验一下被封的感觉）

````java
import org.junit.jupiter.api.Test;
import org.kelab.aide.AideApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.concurrent.CountDownLatch;

/**
 * @author JirathLiu
 * @date 2021/1/24
 * @description:
 */
@SpringBootTest(classes = AideApplication.class)
public class SpringMailTest {
    @Autowired
    JavaMailSender javaMailSender;
    @Value("${spring.mail.username}")
    String from;
    @Test
    void contextLoads() {
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom(from);
        simpleMailMessage.setTo("你自己的QQ号@qq.com");
        simpleMailMessage.setSubject("Test");
        simpleMailMessage.setText("<h1>Test Head?</h1>");
        javaMailSender.send(simpleMailMessage);
    }

    String html = "<table style=\"width: 99.8%; \"><tbody><tr><td id=\"QQMAILSTATIONERY\" style=\"background:url(https://rescdn.qqmail.com/zh_CN/htmledition/images/xinzhi/bg/a_07.jpg) repeat-x #e4ebf5; min-height:550px; padding: 100px 55px 200px;\">test<br></td></tr></tbody></table>";
    @Test
    void sendOneHtml(){
        MimeMessage message = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(from);
            helper.setTo("你自己的QQ号@qq.com");
            helper.setText(html,true);
            helper.setSubject("Test");
            javaMailSender.send(helper.getMimeMessage());

        } catch (MessagingException e) {
            e.printStackTrace();
        }finally {
            System.out.println("Finish");
        }
    }
    private CountDownLatch countDownLatch = new CountDownLatch(500);
    @Test
    void sendBigNumberOfHtml() {
        for (int i = 0; i < 100; i++) {
            new Thread(()->{
                MimeMessage message = javaMailSender.createMimeMessage();
                try {
                    MimeMessageHelper helper = new MimeMessageHelper(message, true);
                    helper.setFrom(from);
                    helper.setTo("你自己的QQ号@qq.com");
                    helper.setText("<h1>Test Head?</h1>");
                    helper.setSubject("Test");
                    javaMailSender.send(helper.getMimeMessage());


                } catch (MessagingException e) {
                    e.printStackTrace();
                }finally {
                    countDownLatch.countDown();
                    System.out.println("Thread"+countDownLatch.getCount());
                }
            }).start();

        }
        try {
            countDownLatch.await();
            System.out.println("Success");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
````

好下面进入正题，我们需要先进行需求分析，然后考虑框架的结构，一步步提高框架的可扩展性。

# 需求分析 | 给点经费吧求求你

本次的需求很简单：学校的邮箱可能会因为学校自己的骚操作（停电，等等等等奇怪的原因）导致挂掉，对于没有接入公众号的系统，这是致命的打击（就靠这玩意儿发消息了），更换QQ邮箱，网易邮箱，又会出现非尊贵的VIP客户而导致的发信频率限制

![贫穷](https://jirath.cn/PicGo/20210319223135.gif)

所以解决问题大致就两个方向：

1. 给点经费氪金
2. 既然一个不保险，就开一个备用，既然一个会cd，就慢慢让他发

综合来看，氪金是不可能的（这么好的机会怎么能浪费了

那么我们只能被（yu）迫（yue）的自研一个小轮子喽

# 相关技术的考虑

那么分析到这里，两个问题困扰着我：

1. 降级
2. 限流

这两个概念在微服务中遇到过，而且很重要。

> 降级：当一个服务变的不可用时，我们不应该继续把压力压上去，而是应该直接放回一个服务不可用或采用备用方案，反正就是其他的逻辑，等一段时间再对原有的服务进行尝试性探测。
>
> 限流：一个服务在应对突然增大的流量很容易炸掉，这里一般使用一个限流操作，让目标匀速的打在服务上。

对于本业务，存在一个特殊的问题，一个邮件可能放了一些关键的信息，是需要保证不丢失的，即消息需要可靠传输。

## 可能的解决方案

1. MQ解决，限流很灵活
2. 使用Java并发工具尝试解决

诶，消息队列做这个可太合适了，消费者生产者模式，速度消费者控制，美汁汁儿~~

但是我们是那种敷衍的人吗？我们不是，我们要尝试挑战自己，我偏要手动实现一个！（真欠打

![我赞爆你](https://jirath.cn/PicGo/20210319223136)

好了，玩归玩，闹归闹，别拿MQ当玩笑（这货真的是最有效的选择

### 那么如何实现降级？

我们可以对一个邮箱账户进行标记，每次处理他时，若发生了异常，我们就进行标记，当失败达到一定次数则进行降级

**这里还存在一个问题，如何恢复？**

很简单，我们记录一下上次错误发生的时间，若超出了我们预设时间，就将其重置，否则加一

### 如何实现限流？

目前所接触到的，主要是两种方式来进行限流（不借助第三方）

1. 限制某种资源的访问数量（可以理解为信号量）
2. 通过计算得到目标资源的访问频率，通过得到的结果进行限制（Sentinel中的限流QPS策略使用的就是这种原理，推荐一篇文章：https://blog.csdn.net/qq924862077/article/details/97423682）

从实践来看，使用QPS在大多数场景下可以把流量控制的更均匀（感谢面试官提出了这个问题

但是QPS的算法需要我们认真考虑，考虑到目前的业务（发送邮件，一个线程进行发送经过测试不会出现问题）

我们直接使用线程数量进行限制即可，利用线程池即可达到我们的预期

# 构建我们的限流降级框架

为了保证我们的代码是优雅的，我们需要了解一些设计模式，来保证框架的扩展性等，例如：模板方法设计模式。

为了使用线程池，我们可以考虑使用Java自带的线程池ExecutorService。

为了保证我们充分利用Spring Cloud的体系，我们还需要考虑能够动态的修改配置，我们需要使用ExecutorService的子类ThreadPoolExecutor，ThreadPoolExecutor中提供了接口用来动态的改变配置。

关于线程池的创建，我们一般有两种创建方式

````java
ExecutorService executorService = Executors.newFixedThreadPool(5);
ThreadPoolExecutor threadPool= new ThreadPoolExecutor(4, 5,
                5L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new MailThreadFactory(),
                this::doThreadOverflow);
````

这两种方式我们采用第二种自定义的模式，使用自定义的模式可以更好的控制线程池，设置自己的线程命名，溢出策略等。

```java
ThreadPoolExecutor threadPool= new ThreadPoolExecutor(1, 2,
                5L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new MailThreadFactory(),
                this::doThreadOverflow);
/**
 * 生产线程，使用了重入锁，保证线程有个唯一的自增id
 * 可优化点：这里的线程数量不能过多，否则会产生id重复(很小几率
 */
private static class MailThreadFactory implements ThreadFactory{
    private volatile int index=0;
    private Lock lock = new ReentrantLock(true);

    @Override
    public Thread newThread(Runnable r) {
        lock.lock();
        try {
            Thread thread = new Thread(r);
            thread.setName("MAIL_THREAD-"+index);
            index++;
            return thread;
        }finally {
            lock.unlock();
        }
    }
}
/**
     * 线程池超量处理
     * 默认丢弃这个任务，发布一个错误
     * 系统正常情况下是不会发生溢出的，将有控制数量的策略
     * @param r
     * @param pool
     */
  protected void doThreadOverflow(Runnable r,ThreadPoolExecutor pool) {
        LOGGER.warn("Mail ThreadPool overflow, this shouldn't be happen normally.");
        LOGGER.warn("pool status: \bcore:{}\nmax:{}\nsize:{}",pool.getCorePoolSize(),pool.getMaximumPoolSize(),pool.getPoolSize());
    }
```

为了抽象邮件任务，邮件账户，我们需要构建两个类Mail，与MailNode

为了增加Mail类的扩展性，我们为其增加两个子类：HtmlMail与SimpleMail，用这两个来区分任务类型（这不重要，这是发送邮件的问题，不是本框架考虑的问题

```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Mail {
    protected List<String> addresses;
    protected String subject;
    protected String content;
    protected String host ;
    protected String username ;
    protected String password ;
    protected String sender ;

    public boolean isLegal() {
        if (addresses==null||addresses.isEmpty()) {
            return false;
        }
        for (String s : addresses) {
            if (! MailCheckUtil.isMail(s)) {
                return false;
            }
        }
        return !StringBaseUtil.isStringListNull(subject,content,sender);
    }
    public boolean canSend(){
        return isLegal()
                && !StringBaseUtil.isStringListNull(host,username,password);
    }
}


/**
 * @author JirathLiu
 * @date 2021/1/31
 * @description:
 */
public class HtmlMail extends Mail{
}

/**
 * @author JirathLiu
 * @date 2021/1/31
 * @description:
 */
public class SimpleMail extends Mail{
}

```

我们为邮箱资源进行抽象，因为底层计划使用Apache的Mail模块，所以需要host，username，password等，为了标记邮箱状态，我们设置错误次数，以及标志位isShutdown(方便查询，使用AtomicInteger要慢一点点)

然后存储上传错误的时间，提供一把锁用来锁定这个节点（后面发现用处不大

因为设计模式的标准，我们需要在这里实现两个方法用来记录错误次数等，为其他类修改提供接口

```java
/**
 * 存储所有的邮箱节点
 */
private class MailNode {
    String host ;
    String username ;
    String password ;
    //失败的次数
    AtomicInteger errTimes=new AtomicInteger(0);
    //是否被关闭
    volatile boolean isShutdown=false;
    //存储最后锁定的时间，若通过，则清除锁定，若失败，则延长锁定
    AtomicLong lastErrTime=new AtomicLong(System.currentTimeMillis());
    Lock lock = new ReentrantLock(true);

    public MailNode(String host, String username, String password) {
        this.host = host;
        this.username = username;
        this.password = password;
    }

    public boolean isLocked() {
        if (isShutdown) {
            long time=lastErrTime.get()+mailHostWaitTime.get();
            if (time < System.currentTimeMillis()) {
                isShutdown=false;
                errTimes.set(0);
                return false;
            }else {
                return true;
            }
        }
        return false;
    }

    /**
     * 记录一个错误，若距离上次错误时间达到了要求，则重新设置为1，否则加一
     */
    public void errRecord() {
        long time=lastErrTime.get()+mailHostWaitTime.get();
        if (time < System.currentTimeMillis()) {
            errTimes.set(1);
        }else {
            errTimes.addAndGet(1);
        }
        lastErrTime.set(System.currentTimeMillis());
        if (errTimes.get() >= maxErrTime) {
            isShutdown=true;
        }
    }
    public String getHost() {
        return host;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

}
```

然后，为了保证框架的性能，在等待队列上是不做限制的，但允许在超出一定长度时，进行报警，我们利用模板方法设计模式，达到目的

```java
/**
 * 提交一个任务
 * 若空余位置达到了阈值，则进行警报
 * lambda表达式，内部为super.send(org.kelab.framework.mail)
 * @param mail
 * @throws MailException
 */
private void submitSend(Mail mail) throws MailException {
    int size = getQueueSize();
    if (size < maxTaskNum.get()) {
        doWarn(threadPool,mails);
    }
    threadPool.submit(() -> doSend(mail));
}

/**
 * 警告处理
 * @param threadPool
 */
protected void doWarn(ThreadPoolExecutor threadPool,List<MailNode> mails) {
}
```

为了达到监控邮箱状态，我们需要自己定制线程池的任务

```java
/**
 *
 * 当系统访问时，若发生异常，
 * 则将记录在错误次数与最后错误时间，并把任务重发。
 *
 * 寻找合适的邮箱，遍历数组，若可使用，则使用，否则跳过，若无可用，则执行报警，并缓存任务，增加一个监听器等待恢复。
 *
 * 每次新记录错误时，
 * 若与最后错误时间在规定时间外，则重新记录
 * 否则增加记录
 *
 * 执行成功后，检查是否需要从redis获取记录
 * @param mail
 * @throws MailException
 */
private void doSend(Mail mail) throws MailException {
    //遍历邮箱，选择第一个可用的邮箱进行尝试
    for (MailNode mailNode : mails) {
        if (!mailNode.isLocked()) {
            copyAttrToMail(mailNode,mail);
            if (!super.send(mail)){
                mailNode.errRecord();
            }else{
                return;
            }
        }
    }
    //全部不能使用，则检查是否开启停顿恢复模式，缓存记录
    failedOperation(mail);
}
/**
     * 失败操作
     * @param mail
     */
    private void failedOperation(Mail mail){
        submitSend(mail);
    }
```

# 为框架增加SpringCloud动态配置刷新机制

为了利用Spring Cloud的动态配置刷新，我们实现两个接口 `ApplicationListener<RefreshScopeRefreshedEvent>, InitializingBean`

每当配置刷新时，会发布一个刷新事件，同时消除已经存在的Bean并进行重构（所以配置一定不能放在本类中）

```java
/**
 * 检查
 * 是否有必要重新构建线程池
 * 是否需要更新邮箱情况
 */
@Override
public void onApplicationEvent(RefreshScopeRefreshedEvent refreshScopeRefreshedEvent) {
    Boolean isEnable=ojMailSenderProperties.getEnableStable();
    if (isEnable != null && isStableOpen != isEnable.booleanValue()) {
        isStableOpen = isEnable;
    }
    //修改邮箱设置，若新设置为空，则不进行更新，并发出警告
    List<MailAccount> accounts = ojMailSenderProperties.getMailList();
    if (accounts==null || accounts.isEmpty()) {
        LOGGER.warn("Mail Account is null, settings keeping.");
    }else {
        List<MailNode> mails=new ArrayList<>();
        for (MailAccount account : accounts) {
            mails.add(new MailNode(account.getHost(), account.getUsername(), account.getPassword()));
        }
        rebuildMails(mails);
    }
    //屏蔽过期设置
    Duration recheckTime=ojMailSenderProperties.getRecheckTime();
    if (recheckTime != null && !recheckTime.isNegative()) {
        if (recheckTime.getSeconds()*1000 != this.mailHostWaitTime.get()) {
            this.mailHostWaitTime.set(recheckTime.getSeconds()*1000);
        }
    }
    //报警设置
    Integer newMaxTaskNum = ojMailSenderProperties.getMaxTaskNum();
    if (newMaxTaskNum != null && maxTaskNum.get()!=newMaxTaskNum.intValue()) {
        maxTaskNum.set(newMaxTaskNum);
    }
    //检查线程池大小
    Integer newThreadNum=ojMailSenderProperties.getTaskRunNum();
    if (newMaxTaskNum != null && newThreadNum != threadPool.getCorePoolSize()) {
        threadPool.setCorePoolSize(newThreadNum);
        threadPool.setMaximumPoolSize(newMaxTaskNum*2);
    }
    //线程存活时间
    Duration newKeepAliveTime=ojMailSenderProperties.getThreadKeepLiveTime();
    if (newKeepAliveTime!=null &&
            newKeepAliveTime.getSeconds()!=threadPool.getKeepAliveTime(TimeUnit.SECONDS)) {
        threadPool.setKeepAliveTime(newKeepAliveTime.getSeconds(),TimeUnit.SECONDS);
    }
}

private void rebuildMails(List<MailNode> newMails) {

}


/**
 * bean属性设置成功后，需要对系统进行预设处理
 * 预设邮箱、线程池
 */
@Override
public void afterPropertiesSet(){
    if (ojMailSenderProperties.getEnableStable() != null) {
        this.isStableOpen=ojMailSenderProperties.getEnableStable();
    }
    //初始化邮箱
    List<MailAccount> accounts = ojMailSenderProperties.getMailList();
    if (accounts==null || accounts.isEmpty()) {
        throw new IllegalArgumentException("Mail accounts can't be empty.");
    }
    mails=new ArrayList<>();
    for (MailAccount account : accounts) {
        mails.add(new MailNode(account.getHost(), account.getUsername(), account.getPassword()));
    }

    //set thread pool
    Integer taskRunNum=ojMailSenderProperties.getTaskRunNum();
    taskRunNum = taskRunNum >1 ? taskRunNum: 1;
    Integer taskRunNum1 = ojMailSenderProperties.getTaskRunNum();
    taskRunNum1 = taskRunNum1 > 5 ? taskRunNum1 : 5;
    Duration threadKeepLiveTime=ojMailSenderProperties.getThreadKeepLiveTime();
    if (threadKeepLiveTime.isNegative()){
        threadKeepLiveTime= Duration.ZERO;
    }

    long keepAliveTime = threadKeepLiveTime.getSeconds();
    TimeUnit timeUnit = TimeUnit.SECONDS;

    Duration recheckTime=ojMailSenderProperties.getRecheckTime();
    long recheckTimeMill = recheckTime.getSeconds() * 1000;

    maxTaskNum = new AtomicInteger(taskRunNum1);
    threadPool.setCorePoolSize(taskRunNum);
    threadPool.setMaximumPoolSize(taskRunNum1 *2);
    threadPool.setKeepAliveTime(keepAliveTime, timeUnit);
    mailHostWaitTime.set(recheckTimeMill);

}
```

搭建完成，芜湖起飞~~

代码都在github啦，欢迎带伙访问，需要你的小star~

[github仓库地址](https://github.com/SWUST-Kelab/swust-oj-mail/tree/main/src/main/java/org/kelab/framework)

都看到这里啦，求求一键三连吧亲

![5IC](https://jirath.cn/PicGo/20210319223300.gif)
