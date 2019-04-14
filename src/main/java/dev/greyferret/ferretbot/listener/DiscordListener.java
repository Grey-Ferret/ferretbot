package dev.greyferret.ferretbot.listener;

import dev.greyferret.ferretbot.processor.DiscordProcessor;
import dev.greyferret.ferretbot.processor.SubVoteProcessor;
import dev.greyferret.ferretbot.config.BotConfig;
import dev.greyferret.ferretbot.config.DiscordConfig;
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
	private BotConfig botConfig;
	@Autowired
	private SubVoteProcessor subVoteProcessor;

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if (botConfig.getSubVoteOn() && event.getChannel() == discordProcessor.subsChannel) {
			subVoteProcessor.processSubVoteMessage(event);
		}
	}
}