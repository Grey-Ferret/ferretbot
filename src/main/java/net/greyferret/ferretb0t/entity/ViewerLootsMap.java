package net.greyferret.ferretb0t.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.persistence.*;
import java.io.Serializable;

/**
 * Class for each "Loots", called Loots
 * <p>
 * Created by GreyFerret on 08.12.2017.
 */
@Entity
@Table(name = "viewer_loots_map")
public class ViewerLootsMap implements Serializable {
	private static final Logger logger = LogManager.getLogger();

	@ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST, optional = true)
	@JoinColumn(name = "viewer_login", referencedColumnName = "login")
	private Viewer viewer;
	@Id
	@Column(name = "loots_Name")
	private String lootsName;

	private ViewerLootsMap() {
	}

	public ViewerLootsMap(String lootsName) {
		this.lootsName = lootsName.toLowerCase();
	}

	public String getLootsName() {
		return lootsName;
	}

	public void setLootsName(String lootsName) {
		this.lootsName = lootsName.toLowerCase();
	}

	public Viewer getViewer() {
		return viewer;
	}

	public void setViewer(Viewer viewer) {
		this.viewer = viewer;
	}
}
