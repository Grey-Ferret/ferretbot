package net.greyferret.ferretbot.client;

import net.greyferret.ferretbot.config.ChatConfig;
import net.greyferret.ferretbot.entity.Viewer;
import net.greyferret.ferretbot.service.ViewerService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Component
public class ViewersClient implements Runnable {
	private static final Logger logger = LogManager.getLogger(ViewersClient.class);

	@Autowired
	private ChatConfig chatConfig;
	@Autowired
	private ViewerService viewerService;
	@Autowired
	private ApplicationContext context;

	private boolean isOn;
	private int checkNumber;
	private HashSet<Viewer> viewersToAddPoints;

	private ViewersClient() {
		isOn = true;
		resetViewersToAddPoints();
	}

	private void resetViewersToAddPoints() {
		checkNumber = 0;
		viewersToAddPoints = new HashSet<>();
	}

	/***
	 * Main run method
	 */
	@Override
	public void run() {
		boolean lastResult = false;
		while (isOn) {
			Integer retryMs;
			if (lastResult == true)
				retryMs = 60000;
			else
				retryMs = 5000;

			try {
				Thread.sleep(retryMs);
			} catch (InterruptedException e) {
				logger.error(e);
			}

			boolean isChannelOnline = context.getBean("isChannelOnline", boolean.class);
			List<String> nicknames = context.getBean("getViewers", ArrayList.class);

			if (nicknames.size() > 1) {
				HashSet<Viewer> viewers = viewerService.checkViewers(nicknames);
				viewersToAddPoints.addAll(viewers);
				logger.info("User list (" + nicknames.size() + ") was refreshed!");
				checkNumber++;
				if (checkNumber >= chatConfig.getUsersCheckMins()) {
					if (isChannelOnline) {
						viewerService.addPointsForViewers(viewersToAddPoints);
						logger.info("Adding points for being on channel for " + viewersToAddPoints.size() + " users");
					}
					resetViewersToAddPoints();
				}
				lastResult = true;
			} else {
				lastResult = false;
			}
		}
	}
}