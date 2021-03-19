package org.kelab.framework.mail.support;

import org.kelab.framework.mail.BasicMailSender;
import org.kelab.framework.mail.Mail;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.kelab.framework.mail.support.OjMailSenderProperties.MailAccount;
/**
 * OJ发送邮件模块
 * 解决的问题：商业公司的邮箱有频率限制，把发信的频率控制下来
 * 目标：降级、削峰
 * 解决方案：构建一个MailSender替代spring boot 默认发信组件
 * 降级：
 *     发信若多次出现异常，则将邮箱更换为下一级，若全部失效，发出警告。
 *     降级后定时对邮箱进行尝试，若原服务恢复，则使用上级服务。
 *     若一个信件发送过程出现异常，则降级后重发。
 * 削峰：
 *     利用线程池的特性，任务将缓存在阻塞队列中，当超出设定长度（积压），执行报警操作
 *
 * 实现模式：收到任务后建立任务队列，发信的执行交给JavaMailSender，本类只需要考虑降级与削峰
 * 允许在配置文件中配置发信邮箱（多级），
 * 配置项目：
 * - 是否开启高压处理（降级与削峰）
 * - 备选邮箱
 * - 线程存活时间
 * - 报警长度
 * - 错误监控时间间隔
 * - 连续错误次数
 *
 * 可优化点：对类结构优化
 * 本框架是为了解决邮箱性能的问题，若实在是没办法，money可能是你的解决方案
 * @author jirath1
 * @date 2021/1/25 下午7:03
 * @description:
 */
@Component
public class OjMailSender extends BasicMailSender implements ApplicationListener<RefreshScopeRefreshedEvent>, InitializingBean {

    @Autowired
    private OjMailSenderProperties ojMailSenderProperties;

    private volatile boolean isStableOpen=false;

    private AtomicInteger maxTaskNum=new AtomicInteger(5000);

    /**
     * 单位：毫秒
     */
    private AtomicLong mailHostWaitTime=new AtomicLong(5000);
    private volatile int maxErrTime=2;
    /**
     * 默认情况：核心线程设置为1，保证只有1个线程在发送邮件，队列长度为20,5个备用线程
     * 使用ThreadPoolExecutor的原因：可以动态调整线程池的状态，对线程池进行监控
     * corePoolSize是同时执行任务的数量，maxSize是超出了阻塞队列大小后的兜底
     * 为了保证任务数量可控，手动控制线程池任务的放置，队列大小为最大。
     */
    ThreadPoolExecutor threadPool= new ThreadPoolExecutor(1, 2,
            5L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),new MailThreadFactory(),this::doThreadOverflow);

    List<MailNode> mails ;

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
    /**
     * 生产线程，使用了重入锁，保证线程有个唯一的自增id
     * 可优化点：这里的线程数量不能过多，否则会产生id重复(很小几率
     */
    private static class MailThreadFactory implements ThreadFactory {
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
    /**
     * 检查数据
     * 若未开启稳定模式，则直接发送邮件
     * @param mail
     * @return
     */
    @Override
    public boolean send(Mail mail) {
        if (mail.isLegal()) {
            if (isStableOpen) {
                submitSend(mail);
                return true;
            }else {
                return super.send(mail);
            }
        }
        return false;
    }


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
    private void copyAttrToMail(MailNode source,Mail ambition){
        ambition.setHost(source.getHost());
        ambition.setUsername(source.getUsername());
        ambition.setPassword(source.getPassword());
    }

    private int getQueueSize() {
        return threadPool.getQueue().size();
    }
    /**
     * 失败操作
     * @param mail
     */
    private void failedOperation(Mail mail){
        submitSend(mail);
    }

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
        List<OjMailSenderProperties.MailAccount> accounts = ojMailSenderProperties.getMailList();
        if (accounts==null || accounts.isEmpty()) {
            LOGGER.warn("Mail Account is null, settings keeping.");
        }else {
            List<MailNode> mails=new ArrayList<>();
            for (OjMailSenderProperties.MailAccount account : accounts) {
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
     * 最高级暴露的控制接口，设置为false不再多次检查邮箱格式
     * @return
     */
    @Override
    protected boolean shouldCheck() {
        return false;
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
}
