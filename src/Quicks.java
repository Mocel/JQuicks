/**
 * 「Javaクイックス開発SDK」1.4
 * 製作／池田プロダクション
 * 開発環境 ： Eclipse 3.1 & JDK 5.0.1 Released by SunMicrosystems Company.
 * 対象環境 ： Windows98/Me/2000/NT/XP JAVA実行対応IE
 * 必要最低環境 ： CPU 333MHz / メモリ  64MB
 * 推奨環境 ： CPU 1GHz / メモリ 128MB
 */

import java.applet.Applet;
import java.applet.AudioClip;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;


/**
 * キーボード入力ができるアプレットKeyTestクラス
 */
public class Quicks extends Applet implements KeyListener, Runnable {

	private static final long serialVersionUID = 1L;

	public static final int UP = 5;
	public static final int DOWN = 6;
	public static final int RIGHT = 7;
	public static final int LEFT = 8;

	public static Quicks INSTANCE;

	/** クリアー面積 何%切るとクリアーになるか */
	int clearRate = 85;
	/** フレームレート 秒間何度画面を再描画更新するか */
	public int frameLate = 60;
	/** 切り取り中の音 */
	public AudioClip cutSound;
	/** 囲み完了音 */
	public AudioClip kakomiSound;
	/** ぶつかったときの音 */
	public AudioClip hitSound;
	/** ゲームオーバーのときの音 */
	public AudioClip gameOverSound;
	/** ステージクリアーのときの音 */
	public AudioClip clearSound;
	/** スタート音 */
	public AudioClip startSound;
	/** メディアトラッカー */
	public MediaTracker tracker;
	/** デフォルトフォント */
	public Font defaultfont;
	/** フォントサイズ */
	public int fontSize = 24;
	/** フォントカラー */
	public Color fontColor;
	/** ゲームオーバーかどうかのフラグ */
	public boolean isGamedOver;
	/** クリアー画像のスイッチ */
	public boolean hasClearImage;

	public boolean isPlaying = true;

	/** クリアーフラグ */
	public boolean isCleared;
	/** ゲームオーバー時メッセージ */
	public String gameOverMessage;
	/** ステージクリアーメッセージ */
	public String clearMessage;
	/** ゲームクリアー時に飛ぶ URL */
	public String clearURL;

	/** 制限時間 */
	public int timeLimit = 1000;
	public int mainTime = 1000;
	/** 制限時間せまってきたときのフラグ */
	public boolean isHurryUp;
	/** 時間制限なしフラグ */
	public boolean isInfinity;

	/** 囲み判定クラスで使うための大域変数 */
	public int xMax;
	public int yMax;
	public int xMin;
	public int yMin;

	/** 移動用回数変数 */
	public int magari;
	/** 敵スレッド */
	public EnemyManager enemyManager;
	/** 時間管理用クラス */
	public TimeManager timeManager;
	/** 背景描画用 */
	public Image backcg;
	/** 前面描画用 */
	public Image frontcg;
	/** マップ */
	public final int[][] map = new int[640][480];
	/** 自分の移動軌跡[]回分の曲がり角を保存 */
	public final int[][] idou = new int[2000][2];
	/** 残りエリア面セクが何 % 切ったか */
	public double parsent;
	/** 自機ライフポイント */
	public int lifePoint = 5;
	/** 死亡クラス */
	public DeadHandler kill = new DeadHandler();
	/** 囲み判定クラス */
	public KakomiHantei kakomi = new KakomiHantei();

	/** 自機クラス */
	public Player player1;
	/** 敵クラスの配列 */
	public Enemy teki[];
	/** 前面描画用オブジェクト プライマリサーフェス */
	public Image priSurface;
	/** 後ろで画面処理を事前に済ませるのに使う */
	public Image backSurface;
	public Image backSurface2;
	public Image backSurface3;
	/** 背景描画メモリ用（ダブルバッファリング) */
	public Graphics backbuf;
	/** 背景描画用メモリその2 */
	public Graphics backbuf2;
	/** 背景描画用メモリその3 */
	public Graphics backbuf3;
	/** 背景描画用メモリその4 */
	public Graphics backbuf4;

	@Override
	public void destroy() {
		// スレッド破棄
		enemyManager.stop();
		timeManager.stop();

		// キーリスナーの切り離し
		removeKeyListener(this);

		// 自身アプレットスレッドの停止
		this.stop();
	}

	@Override
	public void init() {
		INSTANCE = this;

		// アプレットのウィンドウサイズを取得
		Dimension d = getSize();

		// アプレットウィンドウサイズと同じ大きさの描画領域確保
		priSurface = createImage(d.width, d.height);

		backbuf = priSurface.getGraphics();
		backSurface = createImage(d.width, d.height);
		backbuf2 = backSurface.getGraphics();
		backSurface2 = createImage(d.width, d.height);
		backbuf3 = backSurface2.getGraphics();
		backSurface3 = createImage(d.width, d.height);
		backbuf4 = backSurface3.getGraphics();

		// 起動時パラメーターの取得

		// 敵の数
		int n = 3;
		String s = getParameter("teki_kazu");
		if (s != null) {
			try {
				n = Integer.parseInt(s);
				// 1 より小さかったりや 7 より大きかったりすると困るので調整する
				if (n > 7) {
					n = 7;
				} else if (n < 1) {
					n = 1;
				}
			} catch (NumberFormatException ex) {
				ex.printStackTrace();
			}
		}
		teki = new Enemy[n];

		// 制限時間
		s = getParameter("time");
		n = 1000;
		if (s != null) {
			// 時間制限
			try {
				n = Integer.parseInt(s, 10);
				if (n > 1000) {// 4桁以上の数字だったら小さくする
					n = 1000;
				} else if (n < 1) {
					// 時間制限なし
					n = 1000;
					isInfinity = true;
				}
			} catch (NumberFormatException ex) {
				ex.printStackTrace();
			}
		} else {
			// 時間制限なし
			isInfinity = true;
		}
		timeLimit = n;
		mainTime = n;

		s = getParameter("frame_late");
		if (s != null) {// フレームレート
			try {
				n = Integer.parseInt(s, 10);
				if (n > 1000) {
					n = 1000;
				} else if (n < 5) {
					n = 5;
				}
				frameLate = n;// 最低でも5
			} catch (NumberFormatException ex) {
				ex.printStackTrace();
			}
		}

		for (int i = 0, len = teki.length; i < len; i++) {
			s = getParameter("teki" + i + "_speed");
			if (s != null) {
				try {
					n = Integer.parseInt(s, 10);
					if (n > 10) {
						n = 10;
					} else if (n < 1) {
						n = 1;
					}
					teki[i].speed = n;
				} catch (NumberFormatException ex) {
					ex.printStackTrace();
				}
			}
		}

		s = getParameter("parsent");
		if (s != null) {
			try {
				n = Integer.parseInt(s, 10);
				if (n > 100) {
					n = 100;
				} else if (n < 1) {
					n = 1;
				}
				clearRate = n;
			} catch (NumberFormatException ex) {
				ex.printStackTrace();
			}
		}

		// 自機残数
		s = getParameter("kisuu");
		if (s != null) {
			try {
				n = Integer.parseInt(s, 10);
				if (n > 99) {
					n = 99;
				} else if (n < 1) {
					n = 1;
				}
				lifePoint = n;
				player1.life = n;
			} catch (NumberFormatException ex) {
				ex.printStackTrace();
			}
		}

		// フォントサイズ
		s = getParameter("font");
		if (s != null) {
			try {
				n = Integer.parseInt(s, 10);
				if (n > 72) {// 大きすぎると困るので安全処理
					n = 72;
				} else if (n < 12) {
					n = 12;
				}
				fontSize = n;
			} catch (NumberFormatException ex) {
				ex.printStackTrace();
			}
		}

		s = getParameter("color");
		if (s != null) {
			try {
				Color c = Color.decode(s);
				fontColor = c;
			} catch (NumberFormatException ignore) {
			}
		}

		// キーリスナ登録
		addKeyListener(this);

		// フォーカスを得る
		requestFocus();

		// 自機インスタンス 初期位置を与えて生成
		player1 = new Player(1, 1);

		// 敵インスタンス作成
		for (int i = 0, len = teki.length; i < len; i++) {
			teki[i] = new Enemy();
		}

		isCleared = false;// クリアーフラグ
		isGamedOver = false;// ゲームオーバーかどうかのフラグ
		hasClearImage = false;
		xMax = yMax = xMin = yMin = 0;
		parsent = 0;// 切り取り% を０に初期化
		kakomi.par = 0;// 切り取り%を０に初期化

		tracker = new MediaTracker(this);// メディアトラッカーの生成
		LoadImage();// 画像読み込み関数を実行して全ての画像を読み込んでおく

		clearMap();// マップ情報の初期化
		initBackgroundBuffer();// 描画処理バックメモリ領域の初期化

		player1.width = player1.image.getWidth(this);// 自機画像の幅取得
		player1.height = player1.image.getHeight(this);// 自機画像の高さ取得
		player1.kiriWidth = player1.kiriImage.getWidth(this);// 自機切り取り中画像の幅取得
		player1.kiriHeight = player1.kiriImage.getHeight(this);// 自機切り取り中画像の高さ取得

		// 敵画像の幅と高さを取得
		for (Enemy e : teki) {
			e.width = e.gazou.getWidth(this);
			e.height = e.gazou.getHeight(this);
		}

		mainTime = timeLimit;
		timeManager = new TimeManager();// 時間管理用スレッド生成
		timeManager.start();

		enemyManager = new EnemyManager();// 敵管理スレッド生成
		enemyManager.start();
	}

	/**
	 * マップ情報を初期化する。
	 */
	public void clearMap() {
		// マップ情報を 0 でクリア
		for (int x = 0, xLen = map.length; x < xLen; x++) {
			for (int y = 0, yLen = map[x].length; y < yLen; y++) {
				map[x][y] = 0;
			}
		}

		// 上下左右の辺を移動可能領域にする 最初の自分の陣地代わり
		for (int x = 1, xLen = map.length; x < xLen; x++) {
			map[x][1] = 2;
			map[x][477] = 2;
		}
		for (int y = 1, yLen = map[1].length; y < yLen; y++) {
			map[1][y] = 2;
			map[637][y] = 2;
		}

		// 上下左右の辺を囲い済み領域と同等の条件にする 壁代わり
		for (int x = 0, xLen = map.length; x < xLen; x++) {
			map[x][0] = 1;
			map[x][479] = map[x][478] = 1;
		}
		for (int y = 0, yLen = map[0].length; y < yLen; y++) {
			map[0][y] = 1;
			map[639][y] = map[638][y] = 1;
		}
	}

	/**
	 * 使用する画像をファイルから読み込む。
	 */
	public void LoadImage() {
		// 背景画像 画像
		backcg = getImage(getDocumentBase(), getParam("back", "CG/back.jpg"));
		tracker.addImage(backcg, 0);

		// 前面画像 画像
		frontcg = getImage(getDocumentBase(), getParam("front", "CG/front.jpg"));
		tracker.addImage(frontcg, 0);

		// 自機画像
		player1.image = getImage(getDocumentBase(), getParam("player1", "CG/player1.gif"));
		tracker.addImage(player1.image, 1);

		// 自機画像
		player1.kiriImage = getImage(getDocumentBase(), getParam("player1_kiri", "CG/player1_kiri.gif"));
		tracker.addImage(player1.kiriImage, 1);

		for (int i = 0, len = teki.length; i < len; i++) {
			// 敵画像 gif画像
			Image img = getImage(getDocumentBase(), getParam("teki" + i, "CG/teki" + i + ".gif"));
			teki[i].gazou = img;
			tracker.addImage(img, 1);
		}

		// ステージクリアーメッセージ
		clearMessage = getParam("stage_clear_massage", "ステージクリアー！！");

		// ゲームオーバーメッセージ
		gameOverMessage = getParam("gameover_massage", "GAME OVER");

		// クリア後の移動先 URL
		clearURL = getParameter("gameclear_URL");
		if (clearURL != null && clearURL.equalsIgnoreCase("no")) {
			// パラメーター値が no なら無指定にする
			clearURL = null;

		} else {
			// 書かれていなかったらデフォルトのアドレスへ飛ぶ
			clearURL = "./next_stage/index.html";
		}

		defaultfont = new Font("dialog", Font.PLAIN, fontSize);// デフォルトのサイズ

		// 各種音声読み込み
		cutSound = getAudioClip(getDocumentBase(), getParam("kiri", "sound/kiri.wav"));
		kakomiSound = getAudioClip(getDocumentBase(), getParam("kakomi", "sound/kakomi.wav"));
		hitSound = getAudioClip(getDocumentBase(), getParam("butukari", "sound/butukari.wav"));
		gameOverSound = getAudioClip(getDocumentBase(), getParam("gameover", "sound/gameover.wav"));
		clearSound = getAudioClip(getDocumentBase(), getParam("stageclear", "sound/stageclear.wav"));
		startSound = getAudioClip(getDocumentBase(), getParam("start", "sound/start.wav"));
	}

	/**
	 * 描画処理バックメモリ領域を初期化する
	 */
	public void initBackgroundBuffer() {
		try {
			// 全ての画像を読み込むまで待機
			tracker.waitForAll();
		} catch (InterruptedException ignore) {
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.backbuf2.drawImage(this.frontcg, 0, 0, null);
		this.backbuf2.setColor(Color.white);// カラーを白にセット
		this.backbuf3.drawImage(this.frontcg, 0, 0, null);
		this.backbuf3.setColor(Color.white);// カラーを白にセット
		for (int i = 1; i < 639; i++) {// 隅の領域境界線(2の領域）は白に塗る
			if (map[i][1] == 2) {
				backbuf3.drawLine(i, 1, i, 1);
			}
			if (map[i][477] == 2) {
				backbuf3.drawLine(i, 477, i, 477);
			}
		}
		for (int i = 1; i < 479; i++) {
			if (map[1][i] == 2) {
				backbuf3.drawLine(1, i, 1, i);
			}
			if (map[637][i] == 2) {
				backbuf3.drawLine(637, i, 637, i);
			}
		}
		backbuf3.setColor(Color.blue);// カラーを青にセット
		backbuf3.drawRect(0, 0, 639, 479);// 画面一番端の列を青で線を引く
		backbuf3.drawRect(638, 0, 638, 479);
		backbuf3.drawRect(0, 478, 639, 478);
		// 一番端っこの部分をあらためて白で塗りなおす。
		backbuf.setFont(defaultfont);
		backbuf.setColor(Color.white);// カラーを白にセット
		backbuf2.drawImage(backSurface2, 0, 0, null);
	}

	/**
	 * 指定のキーで取得した起動パラメーターを返す。
	 * @param name パラメーター名
	 * @param defaultValue パラメーター値が null の場合に返す値
	 * @return パラメーター値
	 */
	private String getParam(String name, String defaultValue) {
		String result = getParameter(name);
		return result == null ? defaultValue : result;
	}

	/**
	 * ゲームをリスタートする。
	 */
	public void restart() {
		// 敵全部生き返らせる
		for (Enemy e : teki) {
			e.dead = false;
		}

		hasClearImage = false;
		isGamedOver = false;// ゲームオーバーフラグを初期化
		if (! isInfinity) {// 時間制限フラグがあれば
			mainTime = timeLimit;// 時間制限を代入
			isHurryUp = false;
			timeManager.resume();
		}
		player1.life = lifePoint;
		player1.x = 1;// プレイヤー1情報の初期化
		player1.y = 1;
		player1.kiridasi = false;
		xMax = yMin = xMin = yMin = 0;
		parsent = 0;// 切り取り% を０に初期化
		kakomi.par = 0;

		// マップ情報の初期化
		clearMap();

		// スタート音を鳴らす
		startSound.play();

		// 描画処理バックメモリ領域の初期化
		initBackgroundBuffer();

		isPlaying = true;
		enemyManager.resume();
	}

	@Override
	public void keyTyped(KeyEvent e) {
		if (e.getKeyChar() == 'r' || e.getKeyChar() == 'R') {
			// 「R」を押すと
			// ゲーム再起動
			restart();
		}

		if (isGamedOver) {
			// ゲームオーバーの時
			// ゲームをリスタートする
			restart();
		} else if (isCleared) {
			// クリアーの時
			// クリアーしたので画像を全部見れる
			hasClearImage = true;
			if (clearURL != null) {
				// URL指定があるのであればそこへ飛ぶ
				try {
					this.getAppletContext().showDocument(new URL(getDocumentBase(), clearURL));
				} catch (Exception error) {
					System.err.println("URL JUMP Error !!! : " + clearURL);
				}
			} else {
				// URL指定がないとき
				// 描画
				repaint();
			}
		}
	}

	@Override
	public void keyPressed(KeyEvent e) {
		// ゲームオーバー、ステージクリアーの時はここの処理はしない
		if (isGamedOver || isCleared) {
			return;
		}

		int cd = e.getKeyCode();

		// 切り出し検知に使う
		boolean ctrl = e.isControlDown();

		if (magari > 999) {
			kill.dead();
			magari = 0;// 曲がり変数が999を超えたら（999回以上曲がったら）０に戻す
		}

		// まだ切り出していない場合
		if (! player1.kiridasi) {
			// 切り出しボタン押していたら切り出しを開始する
			if (ctrl) {
				magari = 0;// 初期化
				idou[magari][0] = player1.x;
				idou[magari][1] = player1.y;
				magari++;// 一回曲がったことにする
			}
		} else {
			// 切り取り作業中
			// 通った場所のマップを3へ
			int x = player1.x;
			int y = player1.y;
			map[x][y] = 3;

			switch (player1.muki) {
			case LEFT:
				++x;
				break;

			case RIGHT:
				--x;
				break;

			case UP:
				++y;
				break;

			case DOWN:
				--y;
				break;
			}
			map[x][y] = 3;

			// 切り取り音
			cutSound.play();
		}

		switch (cd) {
		case KeyEvent.VK_LEFT:
			// 自機 左移動
			if (player1.x == 1) {
				break;
			}

			// 向きが変わったら 曲がり角を記憶
			if (player1.muki != LEFT) {
				idou[magari][0] = player1.x;
				idou[magari][1] = player1.y;
				magari++;// 一回曲がったことにする
			}

			if (ctrl) {
				if (! player1.kiridasi) {
					// マップにおいて未開拓領域であれば
					if (map[player1.x - 2][player1.y] == 0) {
						// 出発点のマップを3へ
						map[player1.x][player1.y] = 3;
						player1.kiridasi = true;
						if (player1.x >= 2) {
							player1.x -= 2;
						}
						// 通った場所のマップを3へ
						map[player1.x][player1.y] = 3;

						// 現在の向きを左へ設定
						player1.muki = LEFT;
						// 切り出し初期向きを左へ設定
						player1.startMuki = LEFT;
					}
				} else {
					if (map[player1.x - 2][player1.y] == 0) {
						// マップにおいて未開拓領域であれば
						if (player1.x >= 2) {
							player1.x -= 2;
						}
						// 通った場所のマップを3へ
						map[player1.x][player1.y] = 3;
						player1.muki = LEFT;
					} else if (map[player1.x - 2][player1.y] == 2) {
						// 切り取り 完了のとき
						if (player1.x >= 2) {
							player1.x -= 2;
						}
						// 通った場所のマップを3へ
						map[player1.x][player1.y] = map[player1.x + 1][player1.y] = 3;

						player1.muki = LEFT;

						// 切り出し終了向きを左へ設定
						player1.endMuki = LEFT;
						idou[magari][0] = player1.x;
						idou[magari][1] = player1.y;

						// 一回曲がったことにする
						magari++;
						kakomi.kakomi();
					}
				}
			} else {
				if (! player1.kiridasi) {
					if (map[player1.x - 2][player1.y] == 2) {
						// マップにおいて移動可能領域であれば
						player1.x -= 2;
						player1.muki = Quicks.LEFT;
					}
				} else {
					if (map[player1.x - 2][player1.y] == 0) {
						// マップにおいて未開拓領域であれば
						// 通った場所のマップを3へ
						map[player1.x][player1.y] = 3;

						if (player1.x >= 2) {
							player1.x -= 2;
						}
						player1.muki = LEFT;
					} else if (map[player1.x - 2][player1.y] == 2) {
						// 切り取り 完了のとき
						if (player1.x >= 2) {
							player1.x -= 2;
						}

						// 通った場所のマップを3へ
						map[player1.x][player1.y] = map[player1.x + 1][player1.y] = 3;
						player1.muki = LEFT;

						// 切り出し終了向きを左へ設定
						player1.endMuki = LEFT;
						idou[magari][0] = player1.x;
						idou[magari][1] = player1.y;

						// 一回曲がったことにする
						magari++;

						kakomi.kakomi();
					}
				}
			}
			break;

		case KeyEvent.VK_RIGHT:
			// 自機 右移動
			if (player1.x == 637) {
				break;
			}
			if (player1.muki != RIGHT) {
				idou[magari][0] = player1.x;
				idou[magari][1] = player1.y;
				// 一回曲がったことにする
				magari++;
			}

			if (ctrl) {
				if (! player1.kiridasi) {
					if (map[player1.x + 2][player1.y] == 0) {
						// マップにおいて未開拓領域であれば
						// 出発点のマップを3へ
						map[player1.x][player1.y] = 3;
						player1.kiridasi = true;
						if (player1.x <= 639) {
							player1.x += 2;
						}
						player1.muki = RIGHT;
						// 切り出し開始向きを右へ設定
						player1.startMuki = RIGHT;
					}
				} else {
					if (map[player1.x + 2][player1.y] == 0) {
						// マップにおいて未開拓領域であれば
						if (player1.x <= 639) {
							player1.x += 2;
						}
						player1.muki = RIGHT;
					} else if (map[player1.x + 2][player1.y] == 2) {
						// 切り取り 完了のとき
						if (player1.x <= 639) {
							player1.x += 2;
						}

						// 通った場所のマップを3へ
						map[player1.x][player1.y] = map[player1.x - 1][player1.y] = 3;
						player1.muki = RIGHT;

						// 切り出し終了向きを右へ設定
						player1.endMuki = RIGHT;
						idou[magari][0] = player1.x;
						idou[magari][1] = player1.y;

						// 一回曲がったことにする
						magari++;

						kakomi.kakomi();
					}
				}
			} else {
				if (! player1.kiridasi) {
					if (map[player1.x + 2][player1.y] == 2) {
						// マップにおいて移動可能領域であれば
						player1.x += 2;
						player1.muki = RIGHT;
					}
				} else {
					if (map[player1.x + 2][player1.y] == 0) {
						// マップにおいて未開拓領域であれば
						if (player1.x <= 639) {
							player1.x += 2;
						}
						player1.muki = RIGHT;
					} else if (map[player1.x + 2][player1.y] == 2) {
						// 切り取り 完了のとき
						if (player1.x <= 639) {
							player1.x += 2;
						}

						// 通った場所のマップを3へ
						map[player1.x][player1.y] = map[player1.x - 1][player1.y] = 3;
						player1.muki = RIGHT;

						// 切り出し終了向きを右へ設定
						player1.endMuki = RIGHT;
						idou[magari][0] = player1.x;
						idou[magari][1] = player1.y;

						// 一回曲がったことにする
						magari++;

						kakomi.kakomi();
					}
				}
			}
			break;

		case KeyEvent.VK_UP:
			// 自機 上移動
			if (player1.y == 1) {
				break;
			}
			if (player1.muki != UP) {
				idou[magari][0] = player1.x;
				idou[magari][1] = player1.y;
				// 一回曲がったことにする
				magari++;
			}

			if (ctrl) {
				if (! player1.kiridasi) {
					if (map[player1.x][player1.y - 2] == 0) {
						// マップにおいて未開拓領域であれば
						// 出発点のマップを3へ
						map[player1.x][player1.y] = 3;
						player1.kiridasi = true;
						if (player1.y >= 2) {
							player1.y -= 2;
						}
						player1.muki = UP;
						// 切り出し開始向きを上へ設定
						player1.startMuki = UP;
					}
				} else {
					if (map[player1.x][player1.y - 2] == 0) {
						// マップにおいて未開拓領域であれば
						if (player1.y >= 2) {
							player1.y -= 2;
						}
						player1.muki = UP;
					} else if (map[player1.x][player1.y - 2] == 2) {
						// 切り取り 完了のとき
						if (player1.y >= 2) {
							player1.y -= 2;
						}

						// 通った場所のマップを3へ
						map[player1.x][player1.y] = map[player1.x][player1.y + 1] = 3;
						player1.muki = UP;
						// 切り出し終了向きを上へ設定
						player1.endMuki = UP;
						idou[magari][0] = player1.x;
						idou[magari][1] = player1.y;

						// 一回曲がったことにする
						magari++;

						kakomi.kakomi();
					}
				}
			} else {
				if (! player1.kiridasi) {
					if (map[player1.x][player1.y - 2] == 2) {
						// マップにおいて移動可能領域であれば
						player1.y -= 2;
						player1.muki = UP;
					}
				} else {
					if (map[player1.x][player1.y - 2] == 0) {
						// マップにおいて未開拓領域であれば
						if (player1.y >= 2) {
							player1.y -= 2;
						}
						player1.muki = UP;
					} else if (map[player1.x][player1.y - 2] == 2) {
						// 切り取り 完了のとき
						if (player1.y >= 2) {
							player1.y -= 2;
						}
						// 通った場所のマップを3へ
						map[player1.x][player1.y] = map[player1.x][player1.y + 1] = 3;
						player1.muki = UP;

						// 切り出し終了向きを上へ設定
						player1.endMuki = UP;

						idou[magari][0] = player1.x;
						idou[magari][1] = player1.y;

						// 一回曲がったことにする
						magari++;

						kakomi.kakomi();
					}
				}
			}
			break;

		case KeyEvent.VK_DOWN:
			// 自機 下移動
			if (player1.x == 437) {
				break;
			}
			if (player1.muki != DOWN) {
				idou[magari][0] = player1.x;
				idou[magari][1] = player1.y;
				// 一回曲がったことにする
				magari++;
			}

			if (ctrl) {
				if (! player1.kiridasi) {
					if (map[player1.x][player1.y + 2] == 0) {
						// マップにおいて未開拓領域であれば
						// 出発点のマップを3へ
						map[player1.x][player1.y] = 3;
						player1.kiridasi = true;
						if (player1.y <= 479) {
							player1.y += 2;
						}

						player1.muki = DOWN;
						// 切り出し開始向きを下へ設定
						player1.startMuki = DOWN;
					}
				} else {
					if (map[player1.x][player1.y + 2] == 0) {
						// マップにおいて未開拓領域であれば
						if (player1.y <= 479) {
							player1.y += 2;
						}
						player1.muki = DOWN;
					} else if (map[player1.x][player1.y + 2] == 2) {
						// 切り取り 完了のとき
						if (player1.y <= 479) {
							player1.y += 2;
						}
						// 通った場所のマップを3へ
						map[player1.x][player1.y] = map[player1.x][player1.y - 1] = 3;
						player1.muki = Quicks.DOWN;

						// 切り出し終了向きを下へ設定
						player1.endMuki = Quicks.DOWN;

						idou[magari][0] = player1.x;
						idou[magari][1] = player1.y;
						// 一回曲がったことにする
						magari++;

						kakomi.kakomi();
					}
				}
			} else {
				if (! player1.kiridasi) {
					if (map[player1.x][player1.y + 2] == 2) {
						// マップにおいて移動可能領域であれば
						player1.y += 2;
						player1.muki = DOWN;
					}
				} else {
					if (map[player1.x][player1.y + 2] == 0) {
						// マップにおいて未開拓領域であれば
						if (player1.y <= 479) {
							player1.y += 2;
						}
						player1.muki = DOWN;
					} else if (map[player1.x][player1.y + 2] == 2) {
						// 切り取り 完了のとき
						if (player1.y <= 479) {
							player1.y += 2;
						}
						// 通った場所のマップを3へ
						map[player1.x][player1.y] = map[player1.x][player1.y - 1] = 3;

						player1.muki = DOWN;
						// 切り出し終了向きを下へ設定
						player1.endMuki = DOWN;
						idou[magari][0] = player1.x;
						idou[magari][1] = player1.y;

						// 一回曲がったことにする
						magari++;

						kakomi.kakomi();
					}
				}
			}
			break;
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
	}

	@Override
	public void run() {
		long waitMs = 1000L / frameLate;

		startSound.play();// スタート音を鳴らす
		while (true) {// 無限ループ
			try {
				// 描画する
				repaint();

				// 処理遅延時間 つまり、フレームレートの設定。
				Thread.sleep(waitMs);
			} catch (InterruptedException ignore) {
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	@Override
	public void update(Graphics g) {
		// ちらつき防止のため
		// オーバーロードして描画クリアー処理を省いている
		paint(g);
	}

	/**
	 * ゲームオーバー処理。
	 */
	public void endGame() {
		if (! isGamedOver) {
			// ゲームオーバーフラグON
			isGamedOver = true;
			// ゲームオーバーの音を鳴らす
			gameOverSound.play();
		}
		isPlaying = false;
		backbuf.drawString(gameOverMessage, 32, 200);
		backbuf.drawString("何かボタンを押してください", 32, 280 + fontSize);
		backbuf.drawString("リスタートします。", 32, 280 + fontSize + fontSize);
		timeManager.pause();
		enemyManager.pause();
	}

	/**
	 * ゲームクリアー処理
	 */
	public void clearGame() {
		if (! hasClearImage) {
			if (! isCleared) {
				// クリアーフラグON
				isCleared = true;
				// ステージクリアーの音を鳴らす
				clearSound.play();
			}

			// 敵をすべて消す
			for (Enemy enem : teki) {
				enem.dead = true;
			}

			backbuf.drawString(clearMessage, 200, 200);
			backbuf.drawString("何かボタンを押してください。", 200, 200 + fontSize);
		} else {
			// 背景画像の表示
			backbuf.drawImage(backcg, 0, 0, null);
		}

		isPlaying = false;
		if (! isInfinity) {
			// 時間停止
			timeManager.pause();
		}
		// 敵スレッドも停止
		enemyManager.pause();
	}

	@Override
	public void paint(Graphics g) {
		backbuf.drawImage(backSurface2, 0, 0, this);
		if (player1.kiridasi) {
			// カラーを緑にセット
			backbuf.setColor(Color.green);
			// 自機の表示
			backbuf.drawImage(player1.kiriImage, player1.x
					- (player1.kiriWidth / 2), player1.y
					- (player1.kiriHeight / 2), this);

			// 切り取り線の表示
			for (int i = 0, len = magari - 1; i < len; i++) {
				backbuf.drawLine(idou[i][0], idou[i][1], idou[i + 1][0], idou[i + 1][1]);
			}
			// 最後の曲がり角から現在地まで線引き
			backbuf.drawLine(idou[magari - 1][0], idou[magari - 1][1], player1.x, player1.y);
		} else {
			// 自機の表示
			backbuf.drawImage(player1.image,
					(player1.x - ((player1.width) / 2)),
					(player1.y - ((player1.height) / 2)), this);
		}

		for (Enemy e : teki) {
			// 生きている敵の表示
			if (! e.dead) {
				backbuf.drawImage(e.gazou, e.x, e.y, this);
			}
		}

		// カラーを白にセット
		backbuf.setColor(Color.white);
		if (fontColor != null) {
			backbuf.setColor(fontColor);
		}
		if (! isInfinity) {
			// 時間制限あり
			if (isHurryUp) {
				backbuf.setColor(Color.RED);
			}
			// 残機、開拓領域、時間の表示
			backbuf.drawString(
					"自機残量：" + player1.life + "  開拓領域：" + Math.round(parsent)
							+ "%  制限時間：" + mainTime + "秒", 10, 475);
		} else {
			// 時間制限なし
			// 残機、開拓領域
			backbuf.drawString(
					"自機残量：" + player1.life + "  開拓領域：" + Math.round(parsent)
							+ "%", 10, 475);
		}

		if (mainTime < 0 || player1.life <= 0) {// ゲームオーバーならば
			endGame();
		}

		if (Math.round(parsent) >= clearRate) {// ゲームクリアーならば
			clearGame();
		}

		// 裏のメモリ上画像を 表(本当の画面）へ転送
		g.drawImage(priSurface, 0, 0, this);
	}
}

/**
 * 時間管理用クラス
 */
class TimeManager implements Runnable {

	private volatile Thread blinker;
	private volatile boolean suspended;

	public void start() {
		blinker = new Thread(this);
		blinker.start();
	}

	public void stop() {
		blinker = null;
	}

	/**
	 * スレッドが停止されていれば再開させる。
	 */
	public void resume() {
		suspended = false;
	}

	/**
	 * スレッドが停止されていれば再開させる。
	 */
	public void pause() {
		suspended = true;
	}

	public void setPriority(int obj) {
		blinker.setPriority(obj);
	}

	public boolean isAlive() {
		return blinker != null ? blinker.isAlive() : false;
	}

	public void run() {
		Thread thisThread = Thread.currentThread();
		long baseMs = System.currentTimeMillis();
		Quicks q = Quicks.INSTANCE;
		while (blinker == thisThread) {
			if (! suspended) {
				if (! q.isGamedOver && ! q.hasClearImage) {
					if (! q.isHurryUp && q.mainTime < 60) {
						// 時間制限そろそろやヴぁいフラグがまだ立ってない状態で
						// 制限時間が残り60秒を切ったら
						// 時間制限そろそろやヴぁいフラグを立てる
						q.isHurryUp = true;
					}

					// 時間を減らす
					long now = System.currentTimeMillis();
					q.mainTime = q.timeLimit - (int)((now - baseMs) / 1000L);
				}
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException ignore) {
			}
		}
	}
}

/**
 * 敵キャラクター管理用クラス
 *
 */
class EnemyManager implements Runnable {

	private volatile Thread blinker;// スレッド処理用
	private volatile boolean suspended = false;// サスペンド用

	public void start() {// 外部からmyth.start()を呼び出し、スレッドを起動できる
		blinker = new Thread(this);
		blinker.start();
	}

	public void stop() {
		blinker = null;
	}

	/**
	 * スレッドが停止されていれば再開させる。
	 */
	public void resume() {
		suspended = false;
	}

	/**
	 * スレッドが実行中なら停止させる。
	 */
	public void pause() {
		suspended = true;
	}

	public void setPriority(int newPriority) {
		blinker.setPriority(newPriority);
	}

	public boolean isAlive() {
		return blinker != null ? blinker.isAlive() : false;
	}

	public void run() {
		Thread thisThread = Thread.currentThread();

		Enemy[] teki = Quicks.INSTANCE.teki;

		// 処理遅延時間 つまり、フレームレートの設定
		long waitNs = Math.round(1000000000.0d / Quicks.INSTANCE.frameLate);
		long prev = System.nanoTime();

		while (blinker == thisThread) {
			if (! suspended) {
				long now = System.nanoTime();
				if (now - prev >= waitNs) {
					prev = now;
					try {
						for (Enemy e : teki) {
							e.run();
						}
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}

			try {
				Thread.sleep(1);
			} catch (InterruptedException ignore) {
			}
		}
	}
}

/**
 * 囲み判定用クラス
 */
class KakomiHantei {

	double par;// カウンタ、切った％の保持
	int a, b, c, d;
	int temp_min_x = 637, temp_min_y = 477;
	int temp_max_x = 1, temp_max_y = 1;

	int cheakmax_y = 0;
	int cheakmax_x = 0;
	int cheakmin_x = 0;
	int cheakmin_y = 0;
	int po1 = 0;
	int po2 = 0;
	int sarchmuki1 = 0, sarchmuki2 = 0;
	int sarchar1_x = 1;
	int sarchar1_y = 1;
	int sarchar2_x = 1;
	int sarchar2_y = 1;
	int cheak1[][] = new int[10000][2];
	int cheak2[][] = new int[10000][2];
	int turn1 = 0;
	int turn2 = 0;
	int nagasa1 = 0;
	int nagasa2 = 0;
	int startmuki;
	int endmuki;
	boolean flag1 = false;
	int fillrectflag_x;
	int fillrectflag_y;
	int rectNo;
	int linepart_x = 0;// 分割した四角形の横の数
	int linepart_y = 0;// 分割した四角形の縦の数
	int x_num = 0, y_num = 0;// x_line,y_line配列の中身をカウントするため
	int x_line[] = new int[30000];// 切り取り線のx座標を入れる
	int y_line[] = new int[30000];// 切り取り線のy座標を入れる
	int temp_line[][] = new int[50000][2];
	int magattaKazu = 0;
	int magattaKazu1 = 0;
	int firstflag = 0;
	boolean hantei = false;
	int fillrectflag[] = new int[100000];
	int rect_num;

	byte kiri_num = 3;// 切った回数
	int point;// 切り返した点の数

	public void kakomi() {
		Quicks q = Quicks.INSTANCE;

		if (! q.isInfinity) {
			// タイムスレッド一時停止
			q.timeManager.pause();
		}
		q.enemyManager.pause();

		int[][] idou = q.idou;
		int[][] map = q.map;

		a = q.magari;
		for (int i = 0; i < a; i++) {
			if (temp_max_x < idou[i][0]) {
				// x座標の最大値を求める
				temp_max_x = idou[i][0];
			}
			if (temp_max_y < idou[i][1]) {
				// y座標の最大値を求める
				temp_max_y = idou[i][1];
			}
			if (temp_min_x > idou[i][0]) {
				// x座標の最小値を求める
				temp_min_x = idou[i][0];
			}
			if (temp_min_y > idou[i][1]) {
				// y座標の最小値を求める
				temp_min_y = idou[i][1];
			}
		}

		// 切り出し終了点の保持
		sarchar1_x = idou[q.magari - 1][0];
		sarchar1_y = idou[q.magari - 1][1];
		sarchar2_x = idou[q.magari - 1][0];
		sarchar2_y = idou[q.magari - 1][1];

		// 囲み関数が発動した時点での主人公の向き
		sarchmuki2 = sarchmuki1 = q.player1.endMuki;
		nagasa1 = nagasa2 = 0;

		// 囲むべき多角形の全角点を求める

		if (sarchmuki1 == Quicks.UP) {
			if (map[sarchar1_x][sarchar1_y - 1] == 2) {
				sarchmuki1 = Quicks.LEFT;
				sarchmuki2 = Quicks.RIGHT;
			}
		} else if (sarchmuki1 == Quicks.DOWN) {
			if (map[sarchar1_x][sarchar1_y + 1] == 2) {
				sarchmuki1 = Quicks.LEFT;
				sarchmuki2 = Quicks.RIGHT;
			}
		} else if (sarchmuki1 == Quicks.LEFT) {
			if (map[sarchar1_x - 1][sarchar1_y] == 2) {
				sarchmuki1 = Quicks.UP;
				sarchmuki2 = Quicks.DOWN;
			}
		} else if (sarchmuki1 == Quicks.RIGHT) {
			if (map[sarchar1_x + 1][sarchar1_y] == 2) {
				sarchmuki1 = Quicks.UP;
				sarchmuki2 = Quicks.DOWN;
			}
		}
		cheak1[0][0] = sarchar1_x;
		cheak1[0][1] = sarchar1_y;

		po1 = 0;
		while (idou[0][0] != cheak1[po1][0] || idou[0][1] != cheak1[po1][1]) {
			if (sarchmuki1 == Quicks.UP) {
				for (;;) {
					if (idou[0][0] == sarchar1_x && idou[0][1] == sarchar1_y) {
						break;
					} else if (map[sarchar1_x][sarchar1_y - 1] == 2) {
						// まず上を探査
						nagasa1++;
						sarchar1_y = sarchar1_y - 1;
					} else if (map[sarchar1_x - 1][sarchar1_y] == 2) {
						// 続いて左
						sarchmuki1 = Quicks.LEFT;
						break;
					} else if (map[sarchar1_x + 1][sarchar1_y] == 2) {
						// それでもなければ今度は右
						sarchmuki1 = Quicks.RIGHT;
						break;
					} else if (idou[0][0] == sarchar1_x && idou[0][1] == sarchar1_y - 1) {
						// それでもないとすると、すでに到着してるかもしれないのでチェック
						sarchar1_y = sarchar1_y - 1;
						nagasa1++;
						break;
					} else if (idou[0][0] == sarchar1_x - 1 && idou[0][1] == sarchar1_y) {
						sarchar1_x -= 1;
						sarchmuki1 = Quicks.LEFT;
						break;
					} else if (idou[0][0] == sarchar1_x + 1 && idou[0][1] == sarchar1_y) {
						sarchar1_x += 1;
						sarchmuki1 = Quicks.RIGHT;
						break;
					}
				}
			} else if (sarchmuki1 == Quicks.DOWN) {
				for (;;) {
					if (idou[0][0] == sarchar1_x && idou[0][1] == sarchar1_y) {
						break;
					} else if (map[sarchar1_x][sarchar1_y + 1] == 2) {
						sarchar1_y = sarchar1_y + 1;
						nagasa1++;
					} else if (map[sarchar1_x - 1][sarchar1_y] == 2) {
						sarchmuki1 = Quicks.LEFT;
						break;
					} else if (map[sarchar1_x + 1][sarchar1_y] == 2) {
						sarchmuki1 = Quicks.RIGHT;
						break;
					} else if (idou[0][0] == sarchar1_x && idou[0][1] == sarchar1_y + 1) {
						sarchar1_y = sarchar1_y + 1;
						nagasa1++;
						break;
					} else if (idou[0][0] == sarchar1_x - 1 && idou[0][1] == sarchar1_y) {
						sarchar1_x -= 1;
						sarchmuki1 = Quicks.LEFT;
						break;
					} else if (idou[0][0] == sarchar1_x + 1 && idou[0][1] == sarchar1_y) {
						sarchar1_x += 1;
						sarchmuki1 = Quicks.RIGHT;
						break;
					}
				}
			} else if (sarchmuki1 == Quicks.LEFT) {
				for (;;) {
					if (idou[0][0] == sarchar1_x && idou[0][1] == sarchar1_y) {
						break;
					} else if (map[sarchar1_x - 1][sarchar1_y] == 2) {
						sarchar1_x = sarchar1_x - 1;
						nagasa1++;
					} else if (map[sarchar1_x][sarchar1_y - 1] == 2) {
						sarchmuki1 = Quicks.UP;
						break;
					} else if (map[sarchar1_x][sarchar1_y + 1] == 2) {
						sarchmuki1 = Quicks.DOWN;
						break;
					} else if (idou[0][0] == sarchar1_x - 1 && idou[0][1] == sarchar1_y) {
						sarchar1_x = sarchar1_x - 1;
						nagasa1++;
						break;
					} else if (idou[0][0] == sarchar1_x && idou[0][1] == sarchar1_y - 1) {
						sarchar1_y -= 1;
						sarchmuki1 = Quicks.UP;
						break;
					} else if (idou[0][0] == sarchar1_x && idou[0][1] == sarchar1_y + 1) {
						sarchar1_y += 1;
						sarchmuki1 = Quicks.DOWN;
						break;
					}
				}
			} else if (sarchmuki1 == Quicks.RIGHT) {
				for (;;) {
					if (idou[0][0] == sarchar1_x && idou[0][1] == sarchar1_y) {
						break;
					} else if (map[sarchar1_x + 1][sarchar1_y] == 2) {
						sarchar1_x = sarchar1_x + 1;
						nagasa1++;
					} else if (map[sarchar1_x][sarchar1_y - 1] == 2) {
						sarchmuki1 = Quicks.UP;
						break;
					} else if (map[sarchar1_x][sarchar1_y + 1] == 2) {
						sarchmuki1 = Quicks.DOWN;
						break;
					} else if (idou[0][0] == sarchar1_x + 1 && idou[0][1] == sarchar1_y) {
						sarchar1_x = sarchar1_x + 1;
						nagasa1++;
						break;
					} else if (idou[0][0] == sarchar1_x && idou[0][1] == sarchar1_y - 1) {
						sarchar1_y -= 1;
						sarchmuki1 = Quicks.UP;
						break;
					} else if (idou[0][0] == sarchar1_x && idou[0][1] == sarchar1_y + 1) {
						sarchar1_y += 1;
						sarchmuki1 = Quicks.DOWN;
						break;
					}
				}
			}
			po1++;
			cheak1[po1][0] = sarchar1_x;
			cheak1[po1][1] = sarchar1_y;
		}
		cheak1[0][0] = q.player1.x;
		cheak1[0][1] = q.player1.y;

		po2 = 0;
		while (idou[0][0] != cheak2[po2][0] || idou[0][1] != cheak2[po2][1]) {
			if (sarchmuki2 == Quicks.UP) {
				for (;;) {
					if (idou[0][0] == sarchar2_x
							&& idou[0][1] == sarchar2_y) {
						break;
					} else if (map[sarchar2_x][sarchar2_y - 1] == 2) {
						sarchar2_y = sarchar2_y - 1;
						nagasa2++;
					} else if (map[sarchar2_x + 1][sarchar2_y] == 2) {
						sarchmuki2 = Quicks.RIGHT;
						break;
					} else if (map[sarchar2_x - 1][sarchar2_y] == 2) {
						sarchmuki2 = Quicks.LEFT;
						break;
					} else if (idou[0][0] == sarchar2_x && idou[0][1] == sarchar2_y - 1) {
						sarchar2_y = sarchar2_y - 1;
						nagasa2++;
						break;
					} else if (idou[0][0] == sarchar2_x - 1 && idou[0][1] == sarchar2_y) {
						sarchar2_x -= 1;
						sarchmuki2 = Quicks.LEFT;
						break;
					} else if (idou[0][0] == sarchar2_x + 1 && idou[0][1] == sarchar2_y) {
						sarchar2_x += 1;
						sarchmuki2 = Quicks.RIGHT;
						break;
					}
				}
			} else if (sarchmuki2 == Quicks.DOWN) {
				for (;;) {
					if (idou[0][0] == sarchar2_x
							&& idou[0][1] == sarchar2_y) {
						break;
					} else if (map[sarchar2_x][sarchar2_y + 1] == 2) {
						sarchar2_y = sarchar2_y + 1;
						nagasa2++;
					} else if (map[sarchar2_x + 1][sarchar2_y] == 2) {
						sarchmuki2 = Quicks.RIGHT;
						break;
					} else if (map[sarchar2_x - 1][sarchar2_y] == 2) {
						sarchmuki2 = Quicks.LEFT;
						break;
					} else if (idou[0][0] == sarchar2_x && idou[0][1] == sarchar2_y + 1) {
						sarchar2_y = sarchar2_y + 1;
						nagasa2++;
						break;
					} else if (idou[0][0] == sarchar2_x - 1 && idou[0][1] == sarchar2_y) {
						sarchar2_x -= 1;
						sarchmuki2 = Quicks.LEFT;
						break;
					} else if (idou[0][0] == sarchar2_x + 1 && idou[0][1] == sarchar2_y) {
						sarchar2_x += 1;
						sarchmuki2 = Quicks.RIGHT;
						break;
					}
				}
			} else if (sarchmuki2 == Quicks.LEFT) {
				for (;;) {
					if (idou[0][0] == sarchar2_x
							&& idou[0][1] == sarchar2_y) {
						break;
					} else if (map[sarchar2_x - 1][sarchar2_y] == 2) {
						sarchar2_x = sarchar2_x - 1;
						nagasa2++;
					} else if (map[sarchar2_x][sarchar2_y + 1] == 2) {
						sarchmuki2 = Quicks.DOWN;
						break;
					} else if (map[sarchar2_x][sarchar2_y - 1] == 2) {
						sarchmuki2 = Quicks.UP;
						break;
					} else if (idou[0][0] == sarchar2_x - 1 && idou[0][1] == sarchar2_y) {
						sarchar2_x = sarchar2_x - 1;
						nagasa2++;
						break;
					} else if (idou[0][0] == sarchar2_x&& idou[0][1] == sarchar2_y - 1) {
						sarchar2_y -= 1;
						sarchmuki2 = Quicks.UP;
						break;
					} else if (idou[0][0] == sarchar2_x && idou[0][1] == sarchar2_y + 1) {
						sarchar2_y += 1;
						sarchmuki2 = Quicks.DOWN;
						break;
					}
				}
			} else if (sarchmuki2 == Quicks.RIGHT) {
				for (;;) {
					if (idou[0][0] == sarchar2_x
							&& idou[0][1] == sarchar2_y) {
						break;
					} else if (map[sarchar2_x + 1][sarchar2_y] == 2) {
						sarchar2_x = sarchar2_x + 1;
						nagasa2++;
					} else if (map[sarchar2_x][sarchar2_y + 1] == 2) {
						sarchmuki2 = Quicks.DOWN;
						break;
					} else if (map[sarchar2_x][sarchar2_y - 1] == 2) {
						sarchmuki2 = Quicks.UP;
						break;
					} else if (idou[0][0] == sarchar2_x + 1 && idou[0][1] == sarchar2_y) {
						sarchar2_x = sarchar2_x + 1;
						nagasa2++;
						break;
					} else if (idou[0][0] == sarchar2_x && idou[0][1] == sarchar2_y - 1) {
						sarchar2_y -= 1;
						sarchmuki2 = Quicks.UP;
						break;
					} else if (idou[0][0] == sarchar2_x && idou[0][1] == sarchar2_y + 1) {
						sarchar2_y += 1;
						sarchmuki2 = Quicks.DOWN;
						break;
					}
				}
			}
			po2++;
			cheak2[po2][0] = sarchar2_x;
			cheak2[po2][1] = sarchar2_y;
		}
		cheak2[0][0] = q.player1.x;
		cheak2[0][1] = q.player1.y;

		if (nagasa1 < nagasa2) {// 二つの探査方向でより距離が短かったほうを取る。
			flag1 = true;
		} else {
			flag1 = false;
		}

		cheakmax_x = temp_max_x;
		cheakmax_y = temp_max_y;
		cheakmin_x = temp_min_x;
		cheakmin_y = temp_min_y;

		// 開始と終了の方向を求める。
		startmuki = q.player1.startMuki;
		endmuki = q.player1.endMuki;
		// ラベリングするための初期フラグ（"1"のこと）を１つ立てる
		if (startmuki == Quicks.UP) {
			if (endmuki == Quicks.UP) {
				if (flag1) {
					fillrectflag_x = idou[0][0] - 1;
					fillrectflag_y = idou[0][1] - 1;
				} else {
					fillrectflag_x = idou[0][0] + 1;
					fillrectflag_y = idou[0][1] - 1;
				}
			} else if (endmuki == Quicks.DOWN) {
				if (flag1) {
					fillrectflag_x = idou[0][0] + 1;
					fillrectflag_y = idou[0][1] - 1;
				} else {
					fillrectflag_x = idou[0][0] - 1;
					fillrectflag_y = idou[0][1] - 1;
				}
			} else if (endmuki == Quicks.LEFT) {
				if (flag1) {
					fillrectflag_x = idou[0][0] + 1;
					fillrectflag_y = idou[0][1] - 1;
				} else {
					fillrectflag_x = idou[0][0] - 1;
					fillrectflag_y = idou[0][1] - 1;
				}
			} else if (endmuki == Quicks.RIGHT) {
				if (flag1) {
					fillrectflag_x = idou[0][0] - 1;
					fillrectflag_y = idou[0][1] - 1;
				} else {
					fillrectflag_x = idou[0][0] + 1;
					fillrectflag_y = idou[0][1] - 1;
				}
			}
		} else if (startmuki == Quicks.DOWN) {
			if (endmuki == Quicks.UP) {
				if (flag1) {
					fillrectflag_x = idou[0][0] + 1;
					fillrectflag_y = idou[0][1] + 1;
				} else {
					fillrectflag_x = idou[0][0] - 1;
					fillrectflag_y = idou[0][1] + 1;
				}
			} else if (endmuki == Quicks.DOWN) {
				if (flag1) {
					fillrectflag_x = idou[0][0] - 1;
					fillrectflag_y = idou[0][1] + 1;
				} else {
					fillrectflag_x = idou[0][0] + 1;
					fillrectflag_y = idou[0][1] + 1;
				}
			} else if (endmuki == Quicks.LEFT) {
				if (flag1) {
					fillrectflag_x = idou[0][0] - 1;
					fillrectflag_y = idou[0][1] + 1;
				} else {
					fillrectflag_x = idou[0][0] + 1;
					fillrectflag_y = idou[0][1] + 1;
				}
			} else if (endmuki == Quicks.RIGHT) {
				if (flag1) {
					fillrectflag_x = idou[0][0] + 1;
					fillrectflag_y = idou[0][1] + 1;
				} else {
					fillrectflag_x = idou[0][0] - 1;
					fillrectflag_y = idou[0][1] + 1;
				}
			}
		} else if (startmuki == Quicks.LEFT) {
			if (endmuki == Quicks.UP) {
				if (flag1) {
					fillrectflag_x = idou[0][0] - 1;
					fillrectflag_y = idou[0][1] + 1;
				} else {
					fillrectflag_x = idou[0][0] - 1;
					fillrectflag_y = idou[0][1] - 1;
				}
			} else if (endmuki == Quicks.DOWN) {
				if (flag1) {
					fillrectflag_x = idou[0][0] - 1;
					fillrectflag_y = idou[0][1] - 1;
				} else {
					fillrectflag_x = idou[0][0] - 1;
					fillrectflag_y = idou[0][1] + 1;
				}
			} else if (endmuki == Quicks.LEFT) {
				if (flag1) {
					fillrectflag_x = idou[0][0] - 1;
					fillrectflag_y = idou[0][1] - 1;
				} else {
					fillrectflag_x = idou[0][0] - 1;
					fillrectflag_y = idou[0][1] + 1;
				}
			} else if (endmuki == Quicks.RIGHT) {
				if (flag1) {
					fillrectflag_x = idou[0][0] - 1;
					fillrectflag_y = idou[0][1] + 1;
				} else {
					fillrectflag_x = idou[0][0] - 1;
					fillrectflag_y = idou[0][1] - 1;
				}
			}
		} else if (startmuki == Quicks.RIGHT) {
			if (endmuki == Quicks.UP) {
				if (flag1) {
					fillrectflag_x = idou[0][0] + 1;
					fillrectflag_y = idou[0][1] - 1;
				} else {
					fillrectflag_x = idou[0][0] + 1;
					fillrectflag_y = idou[0][1] + 1;
				}
			} else if (endmuki == Quicks.DOWN) {
				if (flag1) {
					fillrectflag_x = idou[0][0] + 1;
					fillrectflag_y = idou[0][1] + 1;
				} else {
					fillrectflag_x = idou[0][0] + 1;
					fillrectflag_y = idou[0][1] - 1;
				}
			} else if (endmuki == Quicks.LEFT) {
				if (flag1) {
					fillrectflag_x = idou[0][0] + 1;
					fillrectflag_y = idou[0][1] + 1;
				} else {
					fillrectflag_x = idou[0][0] + 1;
					fillrectflag_y = idou[0][1] - 1;
				}
			} else if (endmuki == Quicks.RIGHT) {
				if (flag1) {
					fillrectflag_x = idou[0][0] + 1;
					fillrectflag_y = idou[0][1] - 1;
				} else {
					fillrectflag_x = idou[0][0] + 1;
					fillrectflag_y = idou[0][1] + 1;
				}
			}
		}

		if (flag1) {// 曲がり角座標の中で最も大きな座標、最も小さな座標を抜き出す。
			for (c = 1; c <= po1; c++) {
				if (cheakmax_x < cheak1[c][0]) {
					cheakmax_x = cheak1[c][0];
				}
				if (cheakmax_y < cheak1[c][1]) {
					cheakmax_y = cheak1[c][1];
				}
				if (cheakmin_x > cheak1[c][0]) {
					cheakmin_x = cheak1[c][0];
				}
				if (cheakmin_y > cheak1[c][1]) {
					cheakmin_y = cheak1[c][1];
				}
			}
		} else {
			for (c = 1; c <= po2; c++) {
				if (cheakmax_x < cheak2[c][0]) {
					cheakmax_x = cheak2[c][0];
				}
				if (cheakmax_y < cheak2[c][1]) {
					cheakmax_y = cheak2[c][1];
				}
				if (cheakmin_x > cheak2[c][0]) {
					cheakmin_x = cheak2[c][0];
				}
				if (cheakmin_y > cheak2[c][1]) {
					cheakmin_y = cheak2[c][1];
				}
			}
		}

		fillRects();// ラベリング法による 塗りつぶし
		fillLine();// 囲まれた部分の旧境界線を消す

		for (int i = 0; i < 640; i++) {
			for (int j = 0; j < 480; j++) {
				if (map[i][j] == 3) {
					// 切り取り中に通った軌跡
					map[i][j] = 2;
				}
			}
		}

		q.xMax = cheakmax_x;
		q.yMax = cheakmax_y;
		q.xMin = cheakmin_x;
		q.yMin = cheakmin_y;

		// 描画処理バックメモリ領域の更新 関数の呼び出し
		this.updateBackground();
		// 切り出しフラグを OFFへ
		q.player1.kiridasi = false;

		temp_min_x = 637;// 切り取り四角形座標判定変数の初期化
		temp_min_y = 477;
		temp_max_x = 1;
		temp_max_y = 1;

		// スレッド再開
		if (! q.isInfinity) {
			q.timeManager.resume();
		}
		q.enemyManager.resume();

	}

	/**
	 * ライン塗りつぶし処理を実行する。
	 */
	public void fillLine() {
		int[][] map = Quicks.INSTANCE.map;
		if (flag1) {
			for (int i = 0; i < po1; i++) {
				if (cheak1[i][0] == cheak1[i + 1][0]) {
					// xの列が同じ時
					if (cheak1[i][1] > cheak1[i + 1][1]) {
						for (int j = cheak1[i + 1][1]; j <= cheak1[i][1]; j++) {
							map[cheak1[i][0]][j] = 1;
						}
					} else {
						for (int j = cheak1[i][1]; j <= cheak1[i + 1][1]; j++) {
							map[cheak1[i][0]][j] = 1;
						}
					}
				} else if (cheak1[i][1] == cheak1[i + 1][1]) {
					// yの列が同じ時
					if (cheak1[i][0] > cheak1[i + 1][0]) {
						for (int j = cheak1[i + 1][0]; j <= cheak1[i][0]; j++) {
							map[j][cheak1[i][1]] = 1;
						}
					} else {
						for (int j = cheak1[i][0]; j <= cheak1[i + 1][0]; j++) {
							map[j][cheak1[i][1]] = 1;
						}
					}
				}
			}
		} else {
			for (int i = 0; i < po2; i++) {
				if (cheak2[i][0] == cheak2[i + 1][0]) {
					// xの列が同じ時
					if (cheak2[i][1] > cheak2[i + 1][1]) {
						for (int j = cheak2[i + 1][1]; j <= cheak2[i][1]; j++) {
							map[cheak2[i][0]][j] = 1;
						}
					} else {
						for (int j = cheak2[i][1]; j <= cheak2[i + 1][1]; j++) {
							map[cheak2[i][0]][j] = 1;
						}
					}
				} else if (cheak2[i][1] == cheak2[i + 1][1]) {
					// yの列が同じ時
					if (cheak2[i][0] > cheak2[i + 1][0]) {
						for (int j = cheak2[i + 1][0]; j <= cheak2[i][0]; j++) {
							map[j][cheak2[i][1]] = 1;
						}
					} else {
						for (int j = cheak2[i][0]; j <= cheak2[i + 1][0]; j++) {
							map[j][cheak2[i][1]] = 1;
						}
					}
				}
			}
		}

		// 到着点を再度境界線「２」へ
		map[cheak1[po1][0]][cheak1[po1][1]] = 2;
		// 出発点を再度・・・
		map[cheak1[0][0]][cheak1[0][1]] = 2;
	}

	/**
	 * 配列で指定した座標をラインで塗りつぶす。
	 */
	public void fillLine(int cheak[][], int po1) {
		int[][] map = Quicks.INSTANCE.map;
		if (flag1) {
			for (int i = 0; i < po1; i++) {
				if (cheak[i][0] == cheak[i + 1][0]) {
					// xの列が同じ時
					if (cheak[i][1] > cheak[i + 1][1]) {
						for (int j = cheak[i + 1][1]; j <= cheak[i][1]; j++) {
							map[cheak[i][0]][j] = 2;
						}
					} else {
						for (int j = cheak[i][1]; j <= cheak[i + 1][1]; j++) {
							map[cheak[i][0]][j] = 2;
						}
					}
				} else if (cheak[i][1] == cheak[i + 1][1]) {
					// yの列が同じ時
					if (cheak[i][0] > cheak[i + 1][0]) {
						for (int j = cheak[i + 1][0]; j <= cheak[i][0]; j++) {
							map[j][cheak[i][1]] = 2;
						}
					} else {
						for (int j = cheak[i][0]; j <= cheak[i + 1][0]; j++) {
							map[j][cheak[i][1]] = 2;
						}
					}
				}
			}
		}
		// 到着点を再度境界線 ２ へ
		map[cheak[po1][0]][cheak[po1][1]] = 2;
		// 出発点を再度…
		map[cheak[0][0]][cheak[0][1]] = 2;
	}

	/**
	 * 配列で指定した座標をラインで塗りつぶす。
	 */
	public void fillLine(int cheak[][], int po1, int a) {
		int[][] map = Quicks.INSTANCE.map;
		if (flag1) {
			for (int i = 0; i < po1; i++) {
				if (cheak[i][0] == cheak[i + 1][0]) {// xの列が同じ時
					if (cheak[i][1] > cheak[i + 1][1]) {
						for (int j = cheak[i + 1][1]; j <= cheak[i][1]; j++) {
							map[cheak[i][0]][j] = 2;
						}
					} else {
						for (int j = cheak[i][1]; j <= cheak[i + 1][1]; j++) {
							map[cheak[i][0]][j] = 2;
						}
					}
				} else if (cheak[i][1] == cheak[i + 1][1]) {// yの列が同じ時
					if (cheak[i][0] > cheak[i + 1][0]) {
						for (int j = cheak[i + 1][0]; j <= cheak[i][0]; j++) {
							map[j][cheak[i][1]] = 2;
						}
					} else {
						for (int j = cheak[i][0]; j <= cheak[i + 1][0]; j++) {
							map[j][cheak[i][1]] = 2;
						}
					}
				}
			}
		}
		map[cheak[po1][0]][cheak[po1][1]] = 2;// 到着点を再度境界線「２」へ
		map[cheak[0][0]][cheak[0][1]] = 2;// 出発点を再度・・・
	}

	/**
	 * 1 フラグを立てて塗りつぶし処理を実行する。
	 */
	public void fillRects() {
		int i, j;

		Quicks q = Quicks.INSTANCE;
		if (flag1) {
			// まず、自領域の曲がり角の座標を記録する
			for (i = 0; i < po1; i++) {
				x_line[i] = temp_line[i][0] = cheak1[i][0];
				y_line[i] = temp_line[i][1] = cheak1[i][1];
			}
			j = i; // x_line,y_lineにデータを順番に保存するためにiの値を保存する
			// 今回、切り取った曲がり角の座標を記録する
			magattaKazu1 = i;
			for (i = 0; i < q.magari - 1; i++) {
				x_line[i + j] = q.idou[i + 1][0];
				y_line[i + j] = q.idou[i + 1][1];
			}
		} else {
			// まず、自領域の曲がり角の座標を記録する
			for (i = 0; i < po2; i++) {
				x_line[i] = temp_line[i][0] = cheak2[i][0];
				y_line[i] = temp_line[i][1] = cheak2[i][1];
			}
			j = i; // x_line,y_lineにデータを順番に保存するためにiの値を保存する
			// 今回、切り取った曲がり角の座標を記録する
			magattaKazu1 = i;
			for (i = 0; i < q.magari - 1; i++) {
				x_line[i + j] = q.idou[i + 1][0];
				y_line[i + j] = q.idou[i + 1][1];
			}
		}

		magattaKazu = i + j;

		heapsort(x_line, i + j);// ヒープソート
		heapsort(y_line, i + j);

		x_num = sort(x_line, i + j);// ダブっている数値を削除、x_numにはx_line配列の配列長が
		y_num = sort(y_line, i + j);// ダブっている数値を削除、y_numにはy_line配列の配列長が

		// 下準備完了！！

		linepart_x = x_num - 1;// 横の四角の数
		linepart_y = y_num - 1;// 縦の四角の数

		rect_num = linepart_x * linepart_y;// 四角形の数

		for (i = 1; i < x_num; i++) {
			if (fillrectflag_x < x_line[i]) {
				for (j = 1; j < y_num; j++) {
					if (fillrectflag_y < y_line[j]) {
						firstflag = (j - 1) * linepart_x + (i - 1);
						fillrectflag[firstflag] = 1;
						break;
					}
				}
				break;
			}
		}

		checkBorder(firstflag);

		for (i = 0; i < rect_num; i++) {
			if (fillrectflag[i] == 1) {
				fillRectPart(getRectLeft(i), getRectTop(i), getRectRight(i), getRectBottom(i));
			}
		}
	}

	/**
	 * 4 連結を判定する。
	 */
	void checkBorder(int rectNo) {
		int cheakrect_up = 0;
		int cheakrect_down = 0;
		int cheakrect_left = 0;
		int cheakrect_right = 0;
		Deque<Integer> stack = new ArrayDeque<Integer>();

		if (rectNo - linepart_x >= 0
				&& fillrectflag[rectNo - linepart_x] == 0) {
			cheakrect_up = 1;
		}
		if (rectNo + linepart_x < rect_num
				&& fillrectflag[rectNo + linepart_x] == 0) {
			cheakrect_down = 1;
		}
		if (rectNo % linepart_x != 0 && fillrectflag[rectNo - 1] == 0) {
			cheakrect_left = 1;
		}
		if (rectNo % linepart_x != linepart_x - 1
				&& fillrectflag[rectNo + 1] == 0) {
			cheakrect_right = 1;
		}

		int[][] map = Quicks.INSTANCE.map;
		int l = getRectLeft(rectNo);
		int r = getRectRight(rectNo);
		int t = getRectTop(rectNo);
		int b = getRectBottom(rectNo);
		if (cheakrect_up == 1) {
			int n = map[(l + r) / 2][t];
			if (n != 3 && n != 2) {
				fillrectflag[rectNo - linepart_x] = 1;
				stack.addLast(Integer.valueOf(rectNo - linepart_x));
			} else {
				fillrectflag[rectNo - linepart_x] = 2;
			}
		}
		if (cheakrect_down == 1) {
			int n = map[(l + r) / 2][b];
			if (n != 3 && n != 2) {
				fillrectflag[rectNo + linepart_x] = 1;
				stack.addLast(Integer.valueOf(rectNo + linepart_x));
			} else {
				fillrectflag[rectNo + linepart_x] = 2;
			}
		}
		if (cheakrect_left == 1) {
			int n = map[l][(t + b) / 2];
			if (n != 3 && n != 2) {
				fillrectflag[rectNo - 1] = 1;
				stack.addLast(Integer.valueOf(rectNo - 1));
			} else {
				fillrectflag[rectNo - 1] = 2;
			}
		}
		if (cheakrect_right == 1) {
			int n = map[r][(t + b) / 2];
			if (n != 3 && n != 2) {
				fillrectflag[rectNo + 1] = 1;
				stack.addLast(Integer.valueOf(rectNo + 1));
			} else {
				fillrectflag[rectNo + 1] = 2;
			}
		}

		while (! stack.isEmpty()) {
			checkBorder((stack.removeFirst()).intValue());
		}
	}

	/**
	 * 分割された四角形を塗りつぶす。
	 * @param minX 最小の X 座標
	 * @param minY 最小の Y 座標
	 * @param maxX 最大の X 座標
	 * @param maxY 最大の Y 座標
	 */
	void fillRectPart(int minX, int minY, int maxX, int maxY) {
		int[][] map = Quicks.INSTANCE.map;
		for (int i = minX; i <= maxX; i++) {
			for (int j = minY; j <= maxY; j++) {
				if (map[i][j] != 3) {
					map[i][j] = 1;
				}
			}
		}
	}

	/**
	 * 指定された四角形の頂点の左側の X 座標を返す。
	 * @param rectNo 四角形の番号
	 * @return 頂点の X 座標
	 */
	private int getRectLeft(int rectNo) {
		return x_line[rectNo % linepart_x];
	}

	/**
	 * 指定された四角形の頂点の右側の X 座標を返す。
	 * @param rectNo 四角形の番号
	 * @return 頂点の X 座標
	 */
	private int getRectRight(int rectNo) {
		return x_line[(rectNo % linepart_x) + 1];
	}

	/**
	 * 指定された四角形の頂点の上側の Y 座標を返す
	 * @param rectNo 四角形の番号
	 * @return 頂点の Y 座標
	 */
	private int getRectTop(int rectNo) {
		return y_line[rectNo / linepart_x];
	}

	/**
	 * 指定された四角形の頂点の下側の Y 座標を返す
	 * @param rectNo 四角形の番号
	 * @return 頂点の Y 座標
	 */
	private int getRectBottom(int rectNo) {
		return y_line[(rectNo / linepart_x) + 1];
	}

	/**
	 * 背景の描画用メモリ領域を更新する。
	 */
	private void updateBackground() {
		Quicks q = Quicks.INSTANCE;

		// 囲み完了の音を鳴らす
		q.kakomiSound.play();

		// 前回記憶背景画像の表示
		q.backbuf3.drawImage(q.backSurface2, 0, 0, null);

		for (int i = 0; i <= rect_num; i++) {
			drawPartRects(getRectLeft(i), getRectTop(i), getRectRight(i), getRectBottom(i), i);
		}

		// カラーを白にセット
		q.backbuf3.setColor(Color.WHITE);
		int[][] idou = q.idou;
		for (int i = 0, len = q.magari - 2; i <= len; i++) {
			q.backbuf3.drawLine(idou[i][0], idou[i][1], idou[i + 1][0], idou[i + 1][1]);
		}

		// 初期化
		for (int i = 0; i < magattaKazu; i++) {
			temp_line[i][0] = temp_line[i][1] = 0;
			x_line[i] = 0;
			y_line[i] = 0;
		}
		for (int i = 0; i < rect_num + 1; i++) {
			fillrectflag[i] = 0;
		}
		magattaKazu = 0;
		magattaKazu1 = 0;

		// 出来た絵を裏バッファへ転送
		q.backbuf2.drawImage(q.backSurface2, 0, 0, null);
		// 何％切ったか統計を出す
		q.parsent = (par / 290000.0) * 100.0;
	}

	/**
	 * 四角形を描く。
	 */
	private void drawPartRects(int min_x, int min_y, int max_x, int max_y, int z) {
		if (fillrectflag[z] == 1) {
			// フラグが立っている四角形領域であれば塗る
			Quicks.INSTANCE.backbuf3.drawImage(Quicks.INSTANCE.backcg,
					min_x, min_y,
					max_x + 1, max_y + 1,
					min_x, min_y,
					max_x + 1, max_y + 1,
					null);
			// 面積分 囲み領域％へ追加
			par += ((max_x - min_x) * (max_y - min_y));
		}
	}

	/**
	 * 指定の配列をヒープソートする。
	 * @param line ソートする配列
	 * @param line_count ソートする配列の長さ
	 */
	private static void heapsort(int line[], int line_count) {
		int temp;
		for (int i = line_count / 2 - 1; i >= 0; i--) {
			for (int j = i, k; (k = 2 * j + 1) < line_count; j = k) {
				if (k + 1 < line_count) {
					if (line[k] < line[k + 1]) {
						k++;
					}
				}
				if (line[j] >= line[k]) {
					break;
				}
				temp = line[j];
				line[j] = line[k];
				line[k] = temp;
			}
		}
		for (int i = line_count - 1; i > 0; i--) {
			temp = line[0];
			line[0] = line[i];
			line[i] = temp;
			for (int j = 0, k; (k = 2 * j + 1) < i; j = k) {
				if (k + 1 < i) {
					if (line[k] < line[k + 1]) {
						k++;
					}
				}
				if (line[j] >= line[k]) {
					break;
				}
				temp = line[j];
				line[j] = line[k];
				line[k] = temp;
			}
		}
	}

	/**
	 * 指定の配列を線形ソートする。重複している数値は削除し、配列を詰める。
	 * @param a ソートする配列
	 * @param n ソートする配列の長さ
	 */
	private static int sort(int a[], int n) {
		for (int i = 0; i < n; i++) {
			if (a[i] == a[i + 1]) {
				for (int j = i; j < n; j++) {
					a[j + 1] = a[j + 2];
				}
				i--;
				n--;
			}
		}
		return n;
	}
}


/**
 * プレイヤーキャラ情報保持用のクラス
 */
class Player {
	// 位置情報
	public int x;
	public int y;
	// 画像の幅、高さ
	public int width;
	public int height;
	// 切り取り中画像の幅、高さ 情報
	public int kiriWidth;
	public int kiriHeight;
	// 向き情報
	public int muki;
	// 切り出し初期向き
	public int startMuki;
	// 切り出し終了向き
	public int endMuki;
	// 画像情報
	public Image image;
	// 切り出し中の画像
	public Image kiriImage;
	// 切り出しフラグ
	public boolean kiridasi;
	// 残り機体の数
	public int life;
	// プレイヤーの移動速度
	public int speed;

	Player() {
		this(1, 1);
	}

	Player(int X, int Y) {
		this.x = X;
		this.y = Y;
		this.width = 10;
		this.height = 10;
		this.kiriWidth = 10;
		this.kiriHeight = 10;
		this.life = 5;
		this.speed = 2;
	}
}

/**
 * 敵操作用のクラス
 */
class Enemy implements Runnable {

	private static final Random random = new Random();

	// 位置情報
	public int x, y;
	// 向き情報
	public Point muki = new Point();
	// 横幅
	public int width;
	// 縦幅
	public int height;
	// 敵の移動速度
	public int speed;
	// 死亡フラグ
	public boolean dead;
	// 敵 画像読み込み用
	public Image gazou;

	public Enemy() {
		// 初期位置をランダムで決定
		this(random.nextInt(400), random.nextInt(400), random.nextInt(4) + 1, 2);
	}

	public Enemy(int X, int Y, int logic, int speed) {
		this.x = X;
		this.y = Y;
		this.speed = speed;
		this.width = 1;
		this.height = 1;
		// CPU アルゴリズム 行動パターン何で動作するか？
		if (logic < 2) {
			muki.x = muki.y = 1;
		}
		if (logic == 2) {
			muki.x = 1;
			muki.y = -1;
		}
		if (logic == 3) {
			muki.x = muki.y = -1;
		}
		if (logic > 3) {
			muki.x = -1;
			muki.y = 1;
		}
	}

	public void run() {
		Quicks q = Quicks.INSTANCE;

		// 敵が死んでいるときはこの処理は飛ばす
		if (q.isGamedOver || dead) {
			return;
		}

		int[][] map = q.map;
		int i = speed;
		try {
				while (i > 0) {
					i--;
					if (map[(x + (width / 2))][(y + (height / 2))] == 1) {
						// 死亡
						dead = true;
						// ぶつかり音発生
						q.hitSound.play();
					} else if (map[x][y] == 3
							|| map[x + width][y] == 3
							|| map[x][y + height] == 3
							|| map[x + width][y + height] == 3
							|| (q.player1.kiridasi && ! (q.player1.x > (this.x + this.width)
									|| q.player1.y > (this.y + this.height)
									|| (q.player1.x + q.player1.width) < this.x || (q.player1.y + q.player1.height) < this.y))) {
						// 自機の切り取り線軌跡にぶつかったら
						// 死亡処理
						q.kill.dead();
					}
					if (muki.x == 1) {
						// 右向き
						if (map[x + 1 + width][y] == 2
								|| map[x + 1 + width][y + height] == 2) {
							// 敵が自陣地境界線に触れたときの処理
							muki.x = -1;
						} else {
							x++;
						}
					} else if (muki.x == -1) {
						// 左向き
						if (map[x - 1][y] == 2 || map[x - 1][y + height] == 2) {
							// 敵が自陣地境界線に触れたときの処理
							muki.x = 1;
						} else {
							x--;
						}
					}
					if (muki.y == 1) {
						// 下向き
						if (map[x][y + 1 + height] == 2 || map[x + width][y + 1 + height] == 2) {
							// 敵が自陣地境界線に触れたときの処理
							muki.y = -1;
						} else {
							y++;
						}
					} else if (muki.y == -1) {
						// 上向き
						if (map[x][y - 1] == 2 || map[x + width][y - 1] == 2) {
							// 敵が自陣地境界線に触れたときの処理
							muki.y = 1;
						} else {
							y--;
						}
					}
				}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

/**
 * 死亡処理用クラス
 */
class DeadHandler {

	private Point maxPt = new Point();
	private Point minPt = new Point();

	public DeadHandler() {
		init();
	}

	private void init() {
		maxPt.setLocation(1, 1);
		minPt.setLocation(637, 477);
	}

	public void dead() {
		Quicks q = Quicks.INSTANCE;

		q.idou[q.magari][0] = q.player1.x;// 死んだ地点の座標を保持
		q.idou[q.magari][1] = q.player1.y;
		q.magari++;
		q.hitSound.play();// ぶつかった音を鳴らす
		q.player1.life--;// 自機数一つ減らす
		q.player1.kiridasi = false;// 切り出しフラグをＯＦＦ
		for (int i = 0; i < q.magari; i++) {
			if (maxPt.x < q.idou[i][0]) {
				maxPt.x = q.idou[i][0];
			}
			if (maxPt.y < q.idou[i][1]) {
				maxPt.y = q.idou[i][1];
			}
			if (minPt.x > q.idou[i][0]) {
				minPt.x = q.idou[i][0];
			}
			if (minPt.y > q.idou[i][1]) {
				minPt.y = q.idou[i][1];
			}
		}

		for (int i = minPt.x; i <= maxPt.x; i++) {
			for (int j = minPt.y; j <= maxPt.y; j++) {
				if (q.map[i][j] == 3) {
					// 3であれば (切り取り軌跡であれば)
					// 未開拓領域へ
					q.map[i][j] = 0;
				}
			}
		}

		// 出発点も０になってしまっているので直す必要がある
		int x = q.idou[0][0];
		int y = q.idou[0][1];
		// 出発点だけ あらためて２に戻す。
		q.map[x][y] = 2;

		// 切り取り四角形座標判定変数の初期化
		init();

		q.player1.x = q.idou[0][0];// プレイヤーを出発点に戻す
		q.player1.y = q.idou[0][1];
	}
}