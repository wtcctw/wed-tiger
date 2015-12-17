/**
 * 
 */
package com.dianping.wed.tiger.groovy;

import groovy.lang.GroovyClassLoader;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dianping.wed.tiger.ScheduleManagerFactory;
import com.dianping.wed.tiger.annotation.TService;
import com.dianping.wed.tiger.dispatch.DispatchHandler;

/**
 * @author yuantengkai groovy类工厂(单例、多例)
 */
public class GroovyBeanFactory {

	private static final Logger logger = LoggerFactory
			.getLogger(GroovyBeanFactory.class);

	private final GroovyClassLoader gClassLoader = new GroovyClassLoader();

	private static final GroovyBeanFactory instance = new GroovyBeanFactory();

	/**
	 * 约定:单例的groovyHandler开头名称
	 */
	private final String SingleGroovyPrefix = "SGroovy";

	/**
	 * 约定:多例的groovyHandler开头名称
	 */
	private final String PrototypeGroovyPrefix = "PGroovy";

	/**
	 * handler本地缓存map key-handlerName value-handler future
	 */
	private final ConcurrentHashMap<String, Future<DispatchHandler>> handlerCacheMap = new ConcurrentHashMap<String, Future<DispatchHandler>>();

	/**
	 * handler clazz本地缓存map key-handlerName value-handler clazz
	 */
	private final ConcurrentHashMap<String, Class<DispatchHandler>> handlerClazzCacheMap = new ConcurrentHashMap<String, Class<DispatchHandler>>();

	private GroovyBeanFactory() {

	}

	public static GroovyBeanFactory getInstance() {
		return instance;
	}

	/**
	 * 是否是groovy handler类型
	 * 
	 * @param handlerName
	 * @return
	 */
	public boolean isGroovyHandler(String handlerName) {
		if (StringUtils.isBlank(handlerName)) {
			return false;
		}
		if (handlerName.startsWith(SingleGroovyPrefix)
				|| handlerName.startsWith(PrototypeGroovyPrefix)) {
			return true;
		}
		return false;
	}

	/**
	 * 根据handler名字得到任务handler类
	 * 
	 * @param handlerName
	 * @return DispatchHandler
	 */
	public DispatchHandler getHandlerByName(String handlerName) {
		if (StringUtils.isBlank(handlerName)) {
			return null;
		} else if (handlerName.startsWith(SingleGroovyPrefix)) {
			return getHandlerByNameWithSingle(handlerName);
		} else if (handlerName.startsWith(PrototypeGroovyPrefix)) {
			return getHandlerByNameWithPrototype(handlerName);
		} else {
			return null;
		}
	}

	/**
	 * 得到handler clazz
	 * 
	 * @param handlerName
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public Class<DispatchHandler> getClazzByHandlerName(String handlerName) {
		try{
			if (handlerClazzCacheMap.contains(handlerName)) {
				return handlerClazzCacheMap.get(handlerName);
			}
			IGroovyCodeRepo groovyCodeRepo = (IGroovyCodeRepo) ScheduleManagerFactory
					.getBean(IGroovyCodeRepo.BeanName);
			if (groovyCodeRepo == null) {
				throw new RuntimeException("groovyCodeRepo is null.");
			}
			String code = groovyCodeRepo.loadGroovyCodeByHandlerName(handlerName);
			if (StringUtils.isBlank(code)) {
				throw new RuntimeException("groovyCode is blank.");
			}
			if (!isClassTypeHandler(code)) {
				throw new RuntimeException(
						"groovyCode is not DispatchHandler type.");
			}
			Class<DispatchHandler> clazz = gClassLoader.parseClass(code);// 可能刚开始
																			// 同时有多个线程parse同一块code,但不影响业务
			handlerClazzCacheMap.putIfAbsent(handlerName, clazz);
			return handlerClazzCacheMap.get(handlerName);
		}catch(Throwable t){
			logger.error("getHandlerClazz exeption,handlerName="+handlerName, t);
			return null;
		}
	}

	private boolean isClassTypeHandler(String code) {
		if (code.indexOf("tiger.dispatch.DispatchHandler") >= 0) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 得到[单例]的任务handler类
	 * 
	 * @param handlerName
	 * @return DispatchHandler
	 */
	private DispatchHandler getHandlerByNameWithSingle(String handlerName) {
		// 构造DispatchHandler会比较耗时，为了提高性能，这里通过future的方式
		Future<DispatchHandler> fdh = handlerCacheMap.get(handlerName);
		if (fdh == null) {
			FutureTask<DispatchHandler> fTask = new FutureTask<DispatchHandler>(
					new HandlerConstruction(handlerName));
			fdh = handlerCacheMap.putIfAbsent(handlerName, fTask);
			if (fdh == null) {
				fdh = fTask;
				fTask.run();// 只有第一个handlerName的线程去构造DispatchHandler
			}
		}
		DispatchHandler dh = null;
		try {
			dh = fdh.get(200, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			handlerCacheMap.remove(handlerName);
			throw new RuntimeException(e.getCause());
		} catch (ExecutionException e) {
			handlerCacheMap.remove(handlerName);
			throw new RuntimeException(e.getCause());
		} catch (TimeoutException e) {
			handlerCacheMap.remove(handlerName);
			throw new RuntimeException(
					"construct groovyhandler timeoutException,handlerName="
							+ handlerName, e.getCause());
		} catch (Throwable e) {
			handlerCacheMap.remove(handlerName);
			throw new RuntimeException(
					"construct groovyhandler unKnowException,handlerName="
							+ handlerName, e.getCause());
		}
		if (dh instanceof DefaultErrorHandler) {
			handlerCacheMap.remove(handlerName);
		}
		return dh;
	}

	/**
	 * 得到[多例]的任务handler类
	 * 
	 * @param handlerName
	 * @return DispatchHandler
	 */
	private DispatchHandler getHandlerByNameWithPrototype(String handlerName) {
		try {
			Class<DispatchHandler> dhClazz = getClazzByHandlerName(handlerName);
			if(dhClazz == null){
				return new DefaultErrorHandler();
			}
			Field[] fields = dhClazz.getDeclaredFields();
			DispatchHandler handler = dhClazz.newInstance();// 每次都new 一个实例
			for (Field field : fields) {
				TService serviceAnnotation = field
						.getAnnotation(TService.class);
				if (serviceAnnotation != null) {
					Object fieldValue = ScheduleManagerFactory.getBean(field
							.getName());
					if (fieldValue == null) {
						throw new RuntimeException(
								"TService fildValue is null,field="
										+ field.getName());
					}
					field.setAccessible(true);
					field.set(handler, fieldValue);
				}
			}
			return handler;
		} catch (Exception e) {
			logger.error(
					"construct Prototype groovyhandler happens exception,handlerName="
							+ handlerName, e);
			return new DefaultErrorHandler();
		}
	}

	/**
	 * handler构造器
	 * 
	 * @author yuantengkai
	 *
	 */
	private class HandlerConstruction implements Callable<DispatchHandler> {

		private String handlerName;

		public HandlerConstruction(String handlerName) {
			this.handlerName = handlerName;
		}

		@Override
		public DispatchHandler call() throws Exception {
			try {
				Class<DispatchHandler> clazz = getClazzByHandlerName(handlerName);
				if(clazz == null){
					return new DefaultErrorHandler();
				}
				Field[] fields = clazz.getDeclaredFields();
				DispatchHandler handler = clazz.newInstance();
				for (Field field : fields) {
					TService serviceAnnotation = field
							.getAnnotation(TService.class);
					if (serviceAnnotation != null) {
						Object fieldValue = ScheduleManagerFactory
								.getBean(field.getName());
						if (fieldValue == null) {
							throw new RuntimeException(
									"TService fildValue is null,field="
											+ field.getName());
						}
						field.setAccessible(true);
						field.set(handler, fieldValue);
					}
				}
				return handler;
			} catch (Throwable t) {
				logger.error(
						"construct groovyhandler happens exception,handlerName="
								+ handlerName, t);
				return new DefaultErrorHandler();
			}
		}

	}

}
