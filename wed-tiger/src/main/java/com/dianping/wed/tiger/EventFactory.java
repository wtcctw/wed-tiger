/**
 * 
 */
package com.dianping.wed.tiger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dianping.wed.tiger.dispatch.DispatchHandler;
import com.dianping.wed.tiger.dispatch.DispatchMultiService;
import com.dianping.wed.tiger.dispatch.DispatchResultManager;
import com.dianping.wed.tiger.dispatch.DispatchSingleService;
import com.dianping.wed.tiger.dispatch.DispatchTaskEntity;
import com.dianping.wed.tiger.dispatch.DispatchTaskService;
import com.dianping.wed.tiger.event.EventConfig;
import com.dianping.wed.tiger.event.EventConsumer;
import com.dianping.wed.tiger.event.EventExecutor;
import com.dianping.wed.tiger.event.EventFetcher;
import com.dianping.wed.tiger.event.EventFilter;
import com.dianping.wed.tiger.repository.EventInConsumerRepository;

/**
 * @author yuantengkai 事件创建工程
 */
public class EventFactory {

	private static final Logger logger = LoggerFactory
			.getLogger(EventFactory.class);

	/**
	 * 生成任务执行器
	 * 
	 * @param config
	 * @return
	 */
	public static EventExecutor createMultiExecutor(EventConfig config) {
		DispatchTaskService dispatchTaskService = null;
		DispatchMultiService dispatchMultiService = (DispatchMultiService) ScheduleManagerFactory
				.getBean("dispatchMultiService");
		dispatchTaskService = dispatchMultiService;
		// DispatchTaskService dispatchTaskService = (DispatchTaskService)
		// ScheduleManagerFactory
		// .getBean("dispatchTaskService");
		DispatchHandler handler = (DispatchHandler) ScheduleManagerFactory
				.getBean(config.getHandler());
		if (handler == null) {
			logger.warn("there is no handler bean for eventconfig:"
					+ config.getHandler());
			return null;
		}
		EventFetcher eventFetcher = new EventFetcher(dispatchTaskService);
		EventFilter eventFilter = new EventFilter(
				EventInConsumerRepository.getInstance());
		EventExecutor executor = new EventExecutor(config, eventFetcher,
				eventFilter);
		return executor;
	}

	public static EventExecutor createSingleExecutor(EventConfig config) {
		DispatchTaskService dispatchTaskService = null;
		DispatchSingleService dispatchSingleService = (DispatchSingleService) ScheduleManagerFactory
				.getBean("dispatchSingleService");
		dispatchTaskService = dispatchSingleService;
		// DispatchTaskService dispatchTaskService = (DispatchTaskService)
		// ScheduleManagerFactory
		// .getBean("dispatchTaskService");
//		DispatchHandler handler = (DispatchHandler) ScheduleManagerFactory
//				.getBean(config.getHandler());
//		if (handler == null) {
//			logger.warn("there is no handler bean for eventconfig:"
//					+ config.getHandler());
//			return null;
//		}
		EventFetcher eventFetcher = new EventFetcher(dispatchTaskService);
		EventFilter eventFilter = new EventFilter(
				EventInConsumerRepository.getInstance());
		EventExecutor executor = new EventExecutor(config, eventFetcher,
				eventFilter);
		return executor;
	}

	/**
	 * 生成任务消费者
	 * 
	 * @param task
	 * @param handlerName
	 * @return
	 */
	public static EventConsumer createConsumer(DispatchTaskEntity task,
			EventConfig config) {
		DispatchHandler handler = (DispatchHandler) ScheduleManagerFactory
				.getBean(task.getHandler());
		DispatchResultManager resultHandler = DispatchResultManager
				.getInstance();
		if (handler == null) {
			throw new IllegalArgumentException("handler not found,name="
					+ task.getHandler());
		}
		EventConsumer consumer = new EventConsumer(handler, resultHandler,
				task, config.getIdentifyCode());
		return consumer;

	}

}
