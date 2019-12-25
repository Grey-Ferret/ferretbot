package dev.greyferret.ferretbot.service;

import dev.greyferret.ferretbot.entity.GameVoteGame;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Emote;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Log4j2
public class GameVoteGameService {
	@PersistenceContext
	private EntityManager entityManager;
	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Transactional
	public boolean reset(Long textChannelId) {
		try {
			List<GameVoteGame> gameVoteGames = getAllWithTextChannelId(textChannelId);
			for (GameVoteGame gameVoteGame : gameVoteGames) {
				entityManager.remove(gameVoteGame);
			}
			if (gameVoteGames.size() > 0) {
				entityManager.flush();
			}
			return true;
		} catch (Exception ex) {
			log.error(ex.toString());
			return false;
		}
	}

	@Transactional
	public GameVoteGame getByChannelIdAndUserId(Long textChannelId, String userId) {
		CriteriaBuilder builder = entityManagerFactory.getCriteriaBuilder();
		CriteriaQuery<GameVoteGame> criteria = builder.createQuery(GameVoteGame.class);
		Root<GameVoteGame> root = criteria.from(GameVoteGame.class);
		criteria.select(root);
		criteria.where(builder.and(builder.equal(root.get("userId"), userId), builder.equal(root.get("voteChannelId"), textChannelId)));
		try {
			return entityManager.createQuery(criteria).getSingleResult();
		} catch (NoResultException ex) {
			return null;
		}
	}

	@Transactional
	public GameVoteGame getChannelIdAndByGame(Long textChannelId, String game) {
		CriteriaBuilder builder = entityManagerFactory.getCriteriaBuilder();
		CriteriaQuery<GameVoteGame> criteria = builder.createQuery(GameVoteGame.class);
		Root<GameVoteGame> root = criteria.from(GameVoteGame.class);
		criteria.select(root);
		criteria.where(builder.and(builder.equal(root.get("game"), game), builder.equal(root.get("voteChannelId"), textChannelId)));
		try {
			return entityManager.createQuery(criteria).getSingleResult();
		} catch (NoResultException ex) {
			return null;
		}
	}

	@Transactional
	public boolean addOrUpdate(GameVoteGame _gameVoteGame) {
		boolean found = false;
		GameVoteGame gameVoteGame = getByChannelIdAndUserId(_gameVoteGame.getVoteChannelId(), _gameVoteGame.getUserId());
		if (gameVoteGame == null) {
			entityManager.persist(_gameVoteGame);
		} else {
			found = true;
			gameVoteGame.setGame(_gameVoteGame.getGame());
			entityManager.merge(gameVoteGame);
		}
		entityManager.flush();
		return found;
	}

	@Transactional
	public Long findNewEmoteId(Long textChannelId, List<Emote> emotes) {
		HashSet<Long> foundEmotes = getAllEmotes(textChannelId);
		if (foundEmotes.size() >= emotes.size()) {
			return null;
		}
		int idE = -1;
		boolean added = false;
		Emote emote = null;
		while (!added) {
			idE = ThreadLocalRandom.current().nextInt(emotes.size());
			emote = emotes.get(idE);
			if (emote.getRoles() == null || emote.getRoles().size() == 0) {
				added = foundEmotes.add(emote.getIdLong());
			}
		}
		return emote.getIdLong();
	}

	@Transactional
	public HashSet<Long> getAllEmotes(Long textChannelId) {
		List<GameVoteGame> allGames = getAllWithTextChannelId(textChannelId);
		HashSet<Long> emotesCodes = new HashSet<>();
		for (GameVoteGame game : allGames) {
			emotesCodes.add(game.getEmoteId());
		}
		return emotesCodes;
	}

	@Transactional
	public GameVoteGame getGameByChannelIdAndEmoteId(Long textChannelId, long emoteId) {
		CriteriaBuilder builder = entityManagerFactory.getCriteriaBuilder();
		CriteriaQuery<GameVoteGame> criteria = builder.createQuery(GameVoteGame.class);
		Root<GameVoteGame> root = criteria.from(GameVoteGame.class);
		criteria.select(root);
		criteria.where(builder.and(builder.equal(root.get("emoteId"), emoteId), builder.equal(root.get("voteChannelId"), textChannelId)));
		GameVoteGame res = entityManager.createQuery(criteria).getSingleResult();
		return res;
	}

	@Transactional
	public GameVoteGame getGameByChannelIdAndUserId(Long textChannelId, String userId) {
		CriteriaBuilder builder = entityManagerFactory.getCriteriaBuilder();
		CriteriaQuery<GameVoteGame> criteria = builder.createQuery(GameVoteGame.class);
		Root<GameVoteGame> root = criteria.from(GameVoteGame.class);
		criteria.select(root);
		criteria.where(builder.and(builder.equal(root.get("userId"), userId), builder.equal(root.get("voteChannelId"), textChannelId)));
		GameVoteGame res = entityManager.createQuery(criteria).getSingleResult();
		return res;
	}

	@Transactional
	public void addVoter(Long textChannelId, Integer votes, long emoteId, long userId) {
		GameVoteGame game = getGameByChannelIdAndEmoteId(textChannelId, emoteId);
		HashMap<Long, Integer> voters = game.getVoters();
		if (voters == null) {
			voters = new HashMap<>();
		}
		voters.put(userId, votes);
		game.setVoters(voters);
		entityManager.merge(game);
	}

	@Transactional
	public void removeVoter(Long textChannelId, long emoteId, long userId) {
		GameVoteGame game = getGameByChannelIdAndEmoteId(textChannelId, emoteId);
		HashMap<Long, Integer> voters = game.getVoters();
		HashMap<Long, Integer> newVoters = new HashMap<>();
		boolean deleted = false;
		for (Long _userId : voters.keySet()) {
			if (userId != _userId) {
				newVoters.put(_userId, voters.get(_userId));
			} else {
				deleted = true;
			}
		}
		if (deleted) {
			game.setVoters(newVoters);
			entityManager.merge(game);
			entityManager.flush();
		}
	}

	@Transactional
	public boolean clearVoters(Long textChannelId) {
		List<GameVoteGame> games = getAllWithTextChannelId(textChannelId);
		for (GameVoteGame gameVoteGame : games) {
			gameVoteGame.setVoters(new HashMap<>());
			entityManager.merge(gameVoteGame);
		}
		if (games.size() > 0) {
			entityManager.flush();
		}
		return true;
	}

	@Transactional
	public void saveGameForVote(Long textChannelId) {
		List<GameVoteGame> games = getAllWithTextChannelId(textChannelId);
		for (GameVoteGame game : games) {
			game.setInVote(true);
			game.setGameVote(game.getGame());
			entityManager.merge(game);
		}
		if (games != null && games.size() > 0) {
			entityManager.flush();
		}
	}

	@Transactional
	public List<GameVoteGame> getAllWithTextChannelId(Long textChannelId) {
		CriteriaBuilder builder = entityManagerFactory.getCriteriaBuilder();
		CriteriaQuery<GameVoteGame> criteria = builder.createQuery(GameVoteGame.class);
		Root<GameVoteGame> root = criteria.from(GameVoteGame.class);
		criteria.select(root);
		criteria.where(builder.equal(root.get("voteChannelId"), textChannelId));
		return entityManager.createQuery(criteria).getResultList();
	}

	@Transactional
	public String removeGame(Long textChannelId, ArrayList<String> userIds) {
		String res = "Успешно удалены варианты: ";
		ArrayList<String> games = new ArrayList<>();
		for (String userId : userIds) {
			String _game = "";
			try {
				GameVoteGame game = getGameByChannelIdAndUserId(textChannelId, userId);
				_game = game.getGame();
				entityManager.remove(game);
				entityManager.flush();
			} catch (Exception ex) {
				log.error(ex);
				continue;
			}
			games.add(_game);
		}
		if (games.size() == 0) {
			return "Игр от указанных зрителей найдено не было...";
		}
		return res + StringUtils.join(games, ", ");
	}

	@Transactional
	public String removeGame(Long textChannelId, String userId) {
		String res = "Успешно удалён вариант: ";
		String _game = "";
		GameVoteGame game;
		try {
			game = getGameByChannelIdAndUserId(textChannelId, userId);
			_game = game.getGame();
		} catch (Exception ex) {
			log.error(ex);
			return "А был ли вариантик?..";
		}
		entityManager.remove(game);
		entityManager.flush();
		return res + _game;
	}
}