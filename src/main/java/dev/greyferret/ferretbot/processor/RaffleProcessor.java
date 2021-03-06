package dev.greyferret.ferretbot.processor;

import dev.greyferret.ferretbot.config.ApplicationConfig;
import dev.greyferret.ferretbot.config.BotConfig;
import dev.greyferret.ferretbot.config.ViewersConfig;
import dev.greyferret.ferretbot.entity.Prize;
import dev.greyferret.ferretbot.entity.Raffle;
import dev.greyferret.ferretbot.entity.RaffleViewer;
import dev.greyferret.ferretbot.entity.Viewer;
import dev.greyferret.ferretbot.service.PrizePoolService;
import dev.greyferret.ferretbot.service.RaffleService;
import dev.greyferret.ferretbot.service.ViewerService;
import dev.greyferret.ferretbot.util.FerretBotUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Component
@EnableConfigurationProperties({BotConfig.class})
@Log4j2
public class RaffleProcessor implements Runnable, ApplicationListener<ContextStartedEvent> {
    @Value("${main.zone-id}")
    private String zoneId;

    @Autowired
    private ApplicationContext context;
    @Autowired
    private RaffleService raffleService;
    @Autowired
    private ViewerService viewerService;
    @Autowired
    private PrizePoolService prizePoolService;
    @Autowired
    private ApiProcessor apiProcessor;
    @Autowired
    private ApplicationConfig applicationConfig;
    @Autowired
    private PointsProcessor pointsProcessor;
    @Autowired
    private BotConfig botConfig;
    @Autowired
    private ViewersConfig viewersConfig;

    private boolean isOn;
    private HashMap<String, RaffleViewer> raffleViewersMap;
    private final Object RAFFLE_VIEWERS_MAP_OBJ_LOCK = new Object();
    private FerretChatProcessor ferretChatClient;
    private DiscordProcessor discordProcessor;

    @PostConstruct
    private void postConstruct() {
        raffleViewersMap = new HashMap<>();
        isOn = true;

        apiProcessor = context.getBean(ApiProcessor.class);
    }

    @Override
    public void run() {
        ferretChatClient = context.getBean("FerretChatClient", FerretChatProcessor.class);
        discordProcessor = context.getBean(DiscordProcessor.class);
        boolean lastChannelStatus = apiProcessor.getChannelStatus();
        while (isOn) {
            try {
                Thread.sleep(60000);
            } catch (InterruptedException e) {
                log.error(e.toString());
            }

            boolean currentChannelStatus = apiProcessor.getChannelStatus();
            if (currentChannelStatus) {
                Raffle lastRaffle = raffleService.getLast();
                if (lastRaffle == null) {
                    rollRaffle();
                } else {
                    ZonedDateTime lastTodayCal = lastRaffle.getDate(applicationConfig.getZoneId()).plusMinutes(30);

                    if (lastTodayCal.isBefore(ZonedDateTime.now(ZoneId.of(zoneId)))) {
                        if (lastChannelStatus) {
                            rollRaffle();
                        } else {
                            createBlankRaffle();
                        }
                    }
                }
            }
            lastChannelStatus = currentChannelStatus;
        }
    }

    private void createBlankRaffle() {
        Raffle raffle = new Raffle();
        ZonedDateTime zdt = ZonedDateTime.now(ZoneId.of(zoneId)).minusMinutes(20);
        raffle.setDate(zdt);
        raffleService.put(raffle);
    }

    private void rollRaffle() {
        HashSet<Viewer> raffleViewers = new HashSet<>();
        synchronized (RAFFLE_VIEWERS_MAP_OBJ_LOCK) {
            for (RaffleViewer viewer : raffleViewersMap.values()) {
                if (viewer.ifSuitable(ZoneId.of(zoneId))) {
                    Viewer viewerByName = viewerService.getViewerByName(viewer.getLogin());
                    if (viewerByName != null && viewerByName.isSuitableForRaffle()) {
                        raffleViewers.add(viewerByName);
                    }
                }
            }

            final int subLuckModifier = 2;
            ArrayList<Viewer> rollList = FerretBotUtils.combineViewerListWithSubluck(raffleViewers, subLuckModifier);
            Collections.shuffle(rollList);
            boolean isChannelOnline = apiProcessor.getChannelStatus();
            if (isChannelOnline && !rollList.isEmpty()) {
                Viewer viewer = rollList.get(0);
                Prize prize = rollPresent(viewer);
                Raffle raffle = new Raffle(prize, viewer, ZoneId.of(zoneId));

                raffleService.put(raffle);
            }
        }
    }

    private Prize rollPresent(Viewer viewer) {
        Prize prize = prizePoolService.rollPrize();
        String message;
        String messageDiscord;
        int type = 0;
        if (prize == null) {
            int resPts = 50;
            final int chance = 66;
            if (ThreadLocalRandom.current().nextInt(100) > chance) {
                resPts = 100;
                prize = new Prize(resPts + " IQ", 0, type);
            } else {
                prize = new Prize(resPts + " IQ", 0, type);
            }
        } else {
            type = prize.getType();
        }
        if (type == 0) {
            message = " Зритель " + viewer.getLoginVisual() + " стал умнее на " + prize.getName() + "! Поздравляем! ";
            messageDiscord = " Зритель " + FerretBotUtils.escapeNicknameForDiscord(viewer.getLoginVisual()) + " стал умнее на " + prize.getName() + "! Поздравляем! ";
        } else {
            message = " Зритель " + viewer.getLoginVisual() + " выиграл " + prize.getName() + "! Поздравляем! ";
            messageDiscord = " Зритель " + FerretBotUtils.escapeNicknameForDiscord(viewer.getLoginVisual()) + " выиграл " + prize.getName() + "! Поздравляем! ";
        }
        ZonedDateTime zdt = ZonedDateTime.now(ZoneId.of(zoneId));
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.forLanguageTag("ru"));
        ferretChatClient.sendMessage(message);

        if (!applicationConfig.isDebug()) {
            String smileCode = "<a:PepePls:452100407779393536>";
            discordProcessor.raffleChannel.sendMessage(smileCode + messageDiscord + smileCode + dateTimeFormatter.format(zdt)).queue();
        }

        if (message.contains(" IQ!")) {
            String[] split = StringUtils.split(message, ' ');
            int i = 0;
            int j = 0;
            for (String s : split) {
                if (s.equalsIgnoreCase("IQ!")) {
                    i = j - 1;
                    break;
                }
                j++;
            }
            String pointsToChangeString = split[i];
            Long pointsToChange = null;
            try {
                pointsToChange = Long.valueOf(pointsToChangeString);
            } catch (NumberFormatException ex) {
                log.error("There was an error while attempting to convert String to Integer", ex);
            }
            if (pointsToChange != null) {
                pointsProcessor.updatePoints(viewer.getLoginVisual(), pointsToChange);
            }
        }

        resetMessages();
        return prize;
    }

    public void newMessage(String login) {
        synchronized (RAFFLE_VIEWERS_MAP_OBJ_LOCK) {
            login = login.toLowerCase();
            if (!viewersConfig.getRaffleIgnore().contains(login)) {
                Viewer viewerByName = viewerService.getViewerByName(login);
                if (viewerByName == null) {
                    return;
                }
                Boolean follower = viewerByName.getFollower();
                if (follower == null || !follower) {
                    String followDate = apiProcessor.getFollowDate(login);
                    follower = ApiProcessor.isFollowerByFollowedAtString(followDate);
                    viewerService.updateFollowerStatus(viewerByName, followDate, follower);
                    if (!follower) {
                        log.info("User " + login + " was not added to raffle due him not being follower");
                        return;
                    }
                }
                RaffleViewer raffleViewer;
                if (!raffleViewersMap.containsKey(login)) {
                    raffleViewer = new RaffleViewer(login);
                } else {
                    raffleViewer = raffleViewersMap.get(login);
                    raffleViewer.addMessageTime(ZonedDateTime.now(ZoneId.of(zoneId)));
                }
                raffleViewersMap.put(login, raffleViewer);
            }
        }
    }

    public void resetMessages() {
        synchronized (RAFFLE_VIEWERS_MAP_OBJ_LOCK) {
            raffleViewersMap = new HashMap<>();
        }
    }

    @Override
    public void onApplicationEvent(ContextStartedEvent contextStartedEvent) {
        if (botConfig.isRaffleOn()) {
            Thread thread = new Thread(this);
            thread.setName("Raffle Thread");
            thread.start();
            log.info(thread.getName() + " started");
        } else {
            log.info("Raffle off");
        }
    }
}
