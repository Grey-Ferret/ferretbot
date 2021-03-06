package dev.greyferret.ferretbot.processor;

import dev.greyferret.ferretbot.config.BotConfig;
import dev.greyferret.ferretbot.config.ChatConfig;
import dev.greyferret.ferretbot.config.ViewersConfig;
import dev.greyferret.ferretbot.entity.ChatGiftFluffDto;
import dev.greyferret.ferretbot.entity.Viewer;
import dev.greyferret.ferretbot.repository.ChatGiftFluffRepository;
import dev.greyferret.ferretbot.service.ViewerService;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@EnableConfigurationProperties({ViewersConfig.class})
@Log4j2
public class ViewersProcessor implements Runnable, ApplicationListener<ContextStartedEvent> {
	@Autowired
	private ChatConfig chatConfig;
	@Autowired
	private ViewerService viewerService;
	@Autowired
	private ApplicationContext context;
	@Autowired
	private ApiProcessor apiProcessor;
	@Autowired
	private BotConfig botConfig;
	@Autowired
	private ChatGiftFluffRepository chatGiftFluffRepository;

	private boolean isOn;
	private int checkNumber;
	private HashSet<Viewer> viewersToAddPoints;
	private ArrayList<Viewer> viewersToRoll;

	private ViewersProcessor() {
	}

	@PostConstruct
	private void postConstruct() {
		isOn = true;
		apiProcessor = context.getBean(ApiProcessor.class);
		resetViewersToAddPoints();
		viewersToRoll = new ArrayList<>();
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
				retryMs = 300000;
			else
				retryMs = 5000;

			try {
				Thread.sleep(retryMs);
			} catch (InterruptedException e) {
				log.error(e.toString());
			}

			if (botConfig.isViewersPassivePointsOn()) {
				lastResult = checkViewersAndAddPoints();
			} else {
				lastResult = true;
			}
		}
	}

	public void rollSmack(String author) {
		rollSelectedPeople(author, 2);
	}

	private void rollSelectedPeople(String author, int type) {
		ArrayList<Viewer> temp = new ArrayList<>(viewersToAddPoints);
		if (temp != null && temp.size() > 0) {
			viewersToRoll = temp;
		}
		Collections.shuffle(viewersToRoll);
		if (viewersToRoll.size() > 1) {
			Viewer viewer = viewersToRoll.get(0);
			if (!viewer.isSuitableForRaffle()) {
				viewer = viewersToRoll.get(1);
			}
			FerretChatProcessor ferretChatClient = context.getBean("FerretChatClient", FerretChatProcessor.class);
			if (viewer != null) {
				if (type == 1) {
					ferretChatClient.sendMessage(author + " по-дружески обнимает " + viewer.getLoginVisual() + " KappaPride");
				} else if (type == 2) {
					ferretChatClient.sendMessage(author + " отвесил подзатыльник " + viewer.getLoginVisual() + " SMOrc");
				} else if (type == 3) {
					String giftText = rollGiftEntity();
					if (StringUtils.isNotBlank(giftText)) {
						ferretChatClient.sendMessage(author + " подарил(а) " + viewer.getLoginVisual() + " " + giftText + "!");
					}
				}
			}
		}
	}

	private String rollGiftEntity() {
		long count = chatGiftFluffRepository.count();
		if (count == 0) return "";
		int idx = ThreadLocalRandom.current().nextInt((int) count);
		Page<ChatGiftFluffDto> chatGiftFluffDtos = chatGiftFluffRepository.findAll(PageRequest.of(idx, 1));
		return chatGiftFluffDtos.getContent().get(0).getGift();
	}

	public void rollHug(String author) {
		rollSelectedPeople(author, 1);
	}

	public void rollGift(String author) {
		rollSelectedPeople(author, 3);
	}

	private boolean checkViewersAndAddPoints() {
		boolean lastResult;
		boolean isChannelOnline = apiProcessor.getChannelStatus();
		List<String> nicknames = context.getBean("getViewers", ArrayList.class);

		if (nicknames.size() > 1) {
			HashSet<Viewer> viewers = viewerService.checkViewers(nicknames);
			viewersToAddPoints.addAll(viewers);
//				log.info("User list (" + nicknames.size() + ") was refreshed!");
			checkNumber++;
			if (checkNumber >= chatConfig.getUsersCheckMins()) {
				if (isChannelOnline) {
					viewerService.addPointsForViewers(viewersToAddPoints);
					log.info("Adding points for being on channel for " + viewersToAddPoints.size() + " users");
				}
				resetViewersToAddPoints();
			}
			lastResult = true;
		} else {
			lastResult = false;
		}
		return lastResult;
	}

	@Override
	public void onApplicationEvent(ContextStartedEvent contextStartedEvent) {
		Thread thread = new Thread(this);
		thread.setName("Viewer Thread");
		thread.start();
		log.info(thread.getName() + " started");
	}
}
