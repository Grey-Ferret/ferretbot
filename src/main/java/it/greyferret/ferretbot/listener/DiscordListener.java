package it.greyferret.ferretbot.listener;

import it.greyferret.ferretbot.config.BotConfig;
import it.greyferret.ferretbot.config.DiscordConfig;
import it.greyferret.ferretbot.processor.DiscordProcessor;
import it.greyferret.ferretbot.processor.SubVoteProcessor;
import it.greyferret.ferretbot.util.FerretBotUtils;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties({DiscordConfig.class})
public class DiscordListener extends ListenerAdapter {
	private static final Logger logger = LogManager.getLogger(DiscordListener.class);

	@Autowired
	private DiscordProcessor discordProcessor;
	@Autowired
	private DiscordConfig discordConfig;
	@Autowired
	private BotConfig botConfig;
	@Autowired
	private SubVoteProcessor subVoteProcessor;

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if (!event.getMember().getUser().getId().equalsIgnoreCase(discordConfig.getEscapeLogBotId())) {
			if (event.isFromType(ChannelType.PRIVATE)) {
				logger.info("PRIVATE: " + FerretBotUtils.buildDiscordMessageLog(event.getMessage()));
			} else {
				logger.info(FerretBotUtils.buildDiscordMessageLog(event.getMessage()));
			}
		}

		if (botConfig.getSubVoteOn() && event.getChannel() == discordProcessor.subsChannel) {
			subVoteProcessor.processSubVoteMessage(event);
		}
	}
}
