package cz.martykan.webtube;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.ActionBar;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AbsoluteLayout;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import info.guardianproject.netcipher.proxy.OrbotHelper;
import info.guardianproject.netcipher.web.WebkitProxy;


public class MainActivity extends AppCompatActivity {

    public static String LOG_TAG = "webTube";
    WebView webView;
    View appWindow;
    Window window;
    ProgressBar progress;
    View mCustomView;
    FrameLayout customViewContainer;
    DrawerLayout drawerLayout;
    NavigationView navigationView;
    SharedPreferences sp;

    List<String> bookmarkUrls;
    List<String> bookmarkTitles;

    // For the snackbar with error message
    View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            webView.loadUrl(sp.getString("homepage", "https://m.youtube.com/"));
        }
    };

    public static List<JSONObject> asList(final JSONArray ja) {
        final int len = ja.length();
        final ArrayList<JSONObject> result = new ArrayList<JSONObject>(len);
        for (int i = 0; i < len; i++) {
            final JSONObject obj = ja.optJSONObject(i);
            if (obj != null) {
                result.add(obj);
            }
        }
        return result;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Gotta go fast!
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = (WebView) findViewById(R.id.webView);
        appWindow = findViewById(R.id.appWindow);
        window = this.getWindow();
        progress = (ProgressBar) findViewById(R.id.progress);
        customViewContainer = (FrameLayout) findViewById(R.id.customViewContainer);

        sp = PreferenceManager.getDefaultSharedPreferences(this);
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        navigationView = (NavigationView) findViewById(R.id.bookmarks_panel);

        webView.setWebChromeClient(new WebChromeClient() {
            // Fullscreen playback
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (mCustomView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                mCustomView = view;
                webView.loadUrl("javascript:(function() { document.body.style.overflowX = 'hidden'; })();");
                webView.loadUrl("javascript:(function() { window.scrollTo(0, 0); })();");
                drawerLayout.setVisibility(View.GONE);
                customViewContainer.setVisibility(View.VISIBLE);
                customViewContainer.addView(view);

                View decorView = getWindow().getDecorView();
                // Hide the status bar.
                int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
                decorView.setSystemUiVisibility(uiOptions);

            }

            @Override
            public void onHideCustomView() {
                super.onHideCustomView();
                if (mCustomView == null)
                    return;

                webView.loadUrl("javascript:(function() { window.scrollTo(0, 0); })();");
                webView.loadUrl("javascript:(function() { document.body.style.overflowX = 'scroll'; })();");
                drawerLayout.setVisibility(View.VISIBLE);
                customViewContainer.setVisibility(View.GONE);

                mCustomView.setVisibility(View.GONE);
                customViewContainer.removeView(mCustomView);
                mCustomView = null;

                View decorView = getWindow().getDecorView();
                // Show the status bar.
                int uiOptions = View.SYSTEM_UI_FLAG_VISIBLE;
                decorView.setSystemUiVisibility(uiOptions);
            }

            // Progressbar
            public void onProgressChanged(WebView view, int percentage) {
                progress.setVisibility(View.VISIBLE);
                progress.setProgress(percentage);

                // For more advnaced loading status
                if (Integer.valueOf(Build.VERSION.SDK_INT) >= 19) {
                    if (percentage == 100) {
                        progress.setIndeterminate(true);
                    } else {
                        progress.setIndeterminate(false);
                    }
                    webView.evaluateJavascript("(function() { return document.getElementsByClassName('_mks')[0] != null; })();",
                            new ValueCallback<String>() {
                                @Override
                                public void onReceiveValue(String value) {
                                    if (value.equals("false")) {
                                        progress.setVisibility(View.INVISIBLE);
                                    } else {
                                        onProgressChanged(webView, 100);
                                    }
                                }
                            });
                } else {
                    if (percentage == 100) {
                        progress.setVisibility(View.GONE);
                    }
                }
            }


        });

        webView.setWebViewClient(new WebViewClient() {
            // Open links in a browser window (except for sign-in dialogs and YouTube URLs)
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && url.startsWith("http") && !url.contains("accounts.google.") && !url.contains("youtube.")) {
                    view.getContext().startActivity(
                            new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                } else {
                    return false;
                }
            }

            public void onLoadResource(WebView view, String url) {
                if (!url.contains(".jpg") && !url.contains(".ico") && !url.contains(".css") && !url.contains(".js") && !url.contains("complete/search")) {
                    // Remove all iframes (to prevent WebRTC exploits)
                    webView.loadUrl("javascript:(function() {" +
                            "var iframes = document.getElementsByTagName('iframe');" +
                            "for(i=0;i<=iframes.length;i++){" +
                            "if(typeof iframes[0] != 'undefined')" +
                            "iframes[0].outerHTML = '';" +
                            "}})()");

                    // Gets rid of orange outlines
                    if (Integer.valueOf(Build.VERSION.SDK_INT) >= 19) {

                        String css = "*, *:focus { " +
                                " outline: none !important; -webkit-tap-highlight-color: rgba(255,255,255,0) !important; -webkit-tap-highlight-color: transparent !important; }" +
                                " ._mfd { padding-top: 2px !important; } ";
                        webView.loadUrl("javascript:(function() {" +
                                "if(document.getElementById('webTubeStyle') == null){" +
                                "var parent = document.getElementsByTagName('head').item(0);" +
                                "var style = document.createElement('style');" +
                                "style.id = 'webTubeStyle';" +
                                "style.type = 'text/css';" +
                                "style.innerHTML = '" + css + "';" +
                                "parent.appendChild(style);" +
                                "}})()");
                    }

                    // To adapt the statusbar color
                    if (Integer.valueOf(Build.VERSION.SDK_INT) >= 19) {
                        final View statusBarSpace = findViewById(R.id.statusBarSpace);
                        statusBarSpace.setVisibility(View.VISIBLE);
                        webView.evaluateJavascript("(function() { if(document.getElementById('player').style.visibility == 'hidden' || document.getElementById('player').innerHTML == '') { return 'not_video'; } else { return 'video'; } })();",
                                new ValueCallback<String>() {
                                    @Override
                                    public void onReceiveValue(String value) {
                                        if (!value.toString().contains("not_video")) {
                                            statusBarSpace.setBackgroundColor(getApplication().getResources().getColor(R.color.colorWatch));
                                            findViewById(R.id.relativeLayout).setBackgroundColor(getApplication().getResources().getColor(R.color.colorWatch));
                                        } else {
                                            statusBarSpace.setBackgroundColor(getApplication().getResources().getColor(R.color.colorPrimary));
                                            findViewById(R.id.relativeLayout).setBackgroundColor(getApplication().getResources().getColor(R.color.colorPrimary));
                                        }
                                    }
                                });
                    }
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                initalizeBookmarks(navigationView);
            }

            // Deal with error messages
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (description.toString().contains("NETWORK_CHANGED")) {
                    webView.loadUrl(sp.getString("homepage", "https://m.youtube.com/"));
                } else if (description.toString().contains("NAME_NOT_RESOLVED")) {
                    Snackbar.make(appWindow, getString(R.string.errorNoInternet), Snackbar.LENGTH_INDEFINITE).setAction(getString(R.string.refresh), clickListener).show();
                } else if (description.toString().contains("PROXY_CONNECTION_FAILED")) {
                    Snackbar.make(appWindow, getString(R.string.errorTor), Snackbar.LENGTH_INDEFINITE).setAction(getString(R.string.refresh), clickListener).show();
                } else {
                    Snackbar.make(appWindow, getString(R.string.error) + " " + description, Snackbar.LENGTH_INDEFINITE).setAction(getString(R.string.refresh), clickListener).show();
                }
            }
        });

        // Set up webView
        setUpWebview();

        // Initialize bookmarks panel
        initalizeBookmarks(navigationView);
        drawerLayout.setDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                initalizeBookmarks(navigationView);
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                initalizeBookmarks(navigationView);
            }

            @Override
            public void onDrawerClosed(View drawerView) {

            }

            @Override
            public void onDrawerStateChanged(int newState) {

            }
        });
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {
                if (menuItem.getTitle() == getString(R.string.addPage)) {
                    if (!webView.getTitle().equals("YouTube")) {
                        addBookmark(webView.getTitle().replace(" - YouTube", ""), webView.getUrl());
                    }
                } else if (menuItem.getTitle() == getString(R.string.removePage)) {
                    removeBookmark(webView.getTitle().replace(" - YouTube", ""));
                } else {
                    webView.loadUrl(bookmarkUrls.get(bookmarkTitles.indexOf(menuItem.getTitle())));
                    drawerLayout.closeDrawers();
                }
                return true;
            }
        });

        // Floating action buttons
        setUpMenu();

        // Tor
        setUpTor();

        // Load the page
        if (!loadUrlFromIntent(getIntent())) {
            webView.loadUrl(sp.getString("homepage", "https://m.youtube.com/"));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (webView.getUrl().contains("/watch")) {
            if (Integer.valueOf(Build.VERSION.SDK_INT) >= 19) {
                webView.evaluateJavascript("(function() { if(document.getElementsByTagName('video')[0].paused == false) { return 'playing'; } else { return 'stopped'; } })();", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        Log.i("VALUE", value);
                        if (value.equals("\"playing\"")) {
                            showBackgroundPlaybackNotification();
                        }
                    }
                });
            } else {
                showBackgroundPlaybackNotification();
            }
        }
    }

    public void showBackgroundPlaybackNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_headphones_white_24dp)
                .setOngoing(true)
                .setColor(Color.parseColor("#E62118"))
                .setContentTitle(getString(R.string.app_name))
                .setContentText(webView.getTitle().replace(" - YouTube", ""));
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(1337 - 420 * 69, builder.build());
    }

    @Override
    public void onResume() {
        super.onResume();
        cancelNotification();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelNotification();
    }

    private void cancelNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.cancel(1337 - 420 * 69);
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);

        loadUrlFromIntent(intent);
    }

    /**
     * Tries to load URL in WebView if Intent contains required data. Also see Intent filter in manifest.
     *
     * @param intent may contain required data
     * @return {@code true} if data is loaded from URL or URL is already loaded, else {@code false}
     */
    private boolean loadUrlFromIntent(final Intent intent) {

        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            final String url = intent.getData().toString();

            if (url != null && !url.equals(webView.getUrl())) {
                webView.loadUrl(url);
            }

            return true;
        } else {
            return false;
        }
    }

    public void homepageTutorial() {
        if (!sp.getBoolean("homepageLearned", false)) {
            AlertDialog dialog = new AlertDialog.Builder(MainActivity.this).create();
            dialog.setTitle(getString(R.string.home));
            dialog.setMessage(getString(R.string.homePageHelp));
            dialog.setCancelable(false);
            dialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int buttonId) {
                            dialog.dismiss();
                            SharedPreferences.Editor editor = sp.edit();
                            editor.putBoolean("homepageLearned", true);
                            editor.commit();
                        }
                    });
            dialog.show();
        }
    }

    public void setUpWebview() {
        // To save login info
        acceptCookies(true);

        // Some settings
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setAllowFileAccess(false);

        webSettings.setDatabaseEnabled(true);

        String cachePath = this.getApplicationContext()
                .getDir("cache", Context.MODE_PRIVATE).getPath();
        webSettings.setAppCachePath(cachePath);
        webSettings.setAllowFileAccess(true);
        webSettings.setAppCacheEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setHorizontalScrollBarEnabled(false);

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        webView.setBackgroundColor(Color.WHITE);
        webView.setScrollbarFadingEnabled(true);
        webView.setNetworkAvailable(true);

    }

    public void setUpMenu() {
        findViewById(R.id.browserButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                iconAnim(findViewById(R.id.browserButton));
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webView.getUrl())));
            }
        });

        findViewById(R.id.refreshButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                iconAnim(findViewById(R.id.refreshButton));
                webView.reload();
            }
        });

        findViewById(R.id.homeButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                iconAnim(findViewById(R.id.homeButton));
                homepageTutorial();
                webView.loadUrl(sp.getString("homepage", "https://m.youtube.com/"));
            }
        });

        findViewById(R.id.homeButton).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                iconAnim(findViewById(R.id.homeButton));
                Snackbar.make(appWindow, getString(R.string.homePageSet), Snackbar.LENGTH_LONG).show();
                SharedPreferences.Editor editor = sp.edit();
                editor.putString("homepage", webView.getUrl());
                editor.commit();
                return true;
            }
        });


        findViewById(R.id.bookmarksButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawerLayout.openDrawer(findViewById(R.id.bookmarks_panel));
            }
        });

        findViewById(R.id.moreButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(
                        MainActivity.this,
                        android.R.layout.simple_list_item_1);
                arrayAdapter.add(getString(R.string.share));

                PackageManager pm = getPackageManager();
                try {
                    pm.getPackageInfo("org.xbmc.kore", PackageManager.GET_ACTIVITIES);
                    arrayAdapter.add("Cast to Kodi");
                } catch (PackageManager.NameNotFoundException e) {

                }

                if (OrbotHelper.isOrbotInstalled(getApplicationContext())) {
                    if (sp.getBoolean("torEnabled", false) == false) {
                        arrayAdapter.add(getString(R.string.enableTor));
                    } else {
                        arrayAdapter.add(getString(R.string.disableTor));
                    }
                }

                builder.setNegativeButton(
                        getText(R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });

                builder.setAdapter(
                        arrayAdapter,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (arrayAdapter.getItem(which).equals(getString(R.string.share))) {
                                    if (!webView.getUrl().contains("/watch")) {
                                        show_noVideo_dialog();
                                    } else {
                                        Intent shareIntent = new Intent();
                                        shareIntent.setAction(Intent.ACTION_SEND);
                                        shareIntent.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
                                        shareIntent.setType("text/plain");
                                        startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.share_with)));
                                    }
                                } else if (arrayAdapter.getItem(which).equals(getString(R.string.castToKodi))) {
                                    if (!webView.getUrl().contains("/watch")) {
                                        show_noVideo_dialog();
                                    } else {
                                        if (!webView.getUrl().contains("/watch")) {
                                            show_noVideo_dialog();
                                        } else {
                                            try {
                                                /*The following code is based on an extract from the source code of NewPipe (v0.7.2) (https://github.com/theScrabi/NewPipe),
                                                which is also licenced under version 3 of the GNU General Public License as published by the Free Software Foundation.
                                                The copyright owner of the original code is Christian Schabesberger <chris.schabesberger@mailbox.org>.
                                                All modifications were made on 06-Jan-2016*/
                                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                                intent.setPackage("org.xbmc.kore");
                                                intent.setData(Uri.parse(webView.getUrl().replace("https", "http")));
                                                MainActivity.this.startActivity(intent);
                                                /*End of the modified NewPipe code extract*/
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                } else if (arrayAdapter.getItem(which).equals(getString(R.string.enableTor)) || arrayAdapter.getItem(which).equals(getString(R.string.disableTor))) {
                                    if (sp.getBoolean("torEnabled", false)) {
                                        torDisable();
                                    } else {
                                        AlertDialog alert = new AlertDialog.Builder(MainActivity.this).create();
                                        alert.setTitle(getString(R.string.enableTor) + "?");
                                        alert.setMessage(getString(R.string.torWarning));
                                        alert.setCancelable(false);
                                        alert.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.enable),
                                                new DialogInterface.OnClickListener() {
                                                    public void onClick(DialogInterface dialog, int buttonId) {
                                                        torEnable();
                                                    }
                                                });
                                        alert.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.cancel),
                                                new DialogInterface.OnClickListener() {
                                                    public void onClick(DialogInterface dialog, int buttonId) {

                                                    }
                                                });
                                        alert.show();
                                    }
                                }
                            }
                        });
                builder.show();
            }
        });
    }

    private void iconAnim(View icon) {
        Animator iconAnim = ObjectAnimator.ofPropertyValuesHolder(
                icon,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 1.5f, 1f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 1.5f, 1f));
        iconAnim.start();
    }

    private void show_noVideo_dialog() {
        AlertDialog dialog = new AlertDialog.Builder(MainActivity.this).create();
        dialog.setTitle(getString(R.string.error_no_video));
        dialog.setMessage(getString(R.string.error_select_video_and_retry));
        dialog.setCancelable(true);
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int buttonId) {
                        dialog.dismiss();
                    }
                });
        dialog.show();
    }

    public void initalizeBookmarks(NavigationView navigationView) {
        bookmarkUrls = new ArrayList<String>();
        bookmarkTitles = new ArrayList<String>();

        final Menu menu = navigationView.getMenu();
        menu.clear();
        String result = sp.getString("bookmarks", "[]");
        try {
            JSONArray bookmarksArray = new JSONArray(result);
            for (int i = 0; i < bookmarksArray.length(); i++) {
                JSONObject bookmark = bookmarksArray.getJSONObject(i);
                menu.add(bookmark.getString("title")).setIcon(R.drawable.ic_star_grey600_24dp);
                bookmarkTitles.add(bookmark.getString("title"));
                bookmarkUrls.add(bookmark.getString("url"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (!bookmarkUrls.contains(webView.getUrl())) {
            menu.add(getString(R.string.addPage)).setIcon(R.drawable.ic_plus_grey600_24dp);
        } else {
            menu.add(getString(R.string.removePage)).setIcon(R.drawable.ic_close_grey600_24dp);
        }
    }

    public void addBookmark(String title, String url) {
        String result = sp.getString("bookmarks", "[]");
        try {
            JSONArray bookmarksArray = new JSONArray(result);
            bookmarksArray.put(new JSONObject("{'title':'" + title + "','url':'" + url + "'}"));
            SharedPreferences.Editor editor = sp.edit();
            editor.putString("bookmarks", bookmarksArray.toString());
            editor.commit();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        initalizeBookmarks(navigationView);
    }

    public void removeBookmark(String title) {
        String result = sp.getString("bookmarks", "[]");
        try {
            JSONArray bookmarksArray = new JSONArray(result);
            if (Integer.valueOf(Build.VERSION.SDK_INT) >= 19) {
                bookmarksArray.remove(bookmarkTitles.indexOf(title));
            } else {
                final List<JSONObject> objs = asList(bookmarksArray);
                objs.remove(bookmarkTitles.indexOf(title));
                final JSONArray out = new JSONArray();
                for (final JSONObject obj : objs) {
                    out.put(obj);
                }
                bookmarksArray = out;
            }
            SharedPreferences.Editor editor = sp.edit();
            editor.putString("bookmarks", bookmarksArray.toString());
            editor.commit();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        initalizeBookmarks(navigationView);
    }

    public void setUpTor() {
        // Tor
        if (OrbotHelper.isOrbotInstalled(getApplicationContext())) {
            if (sp.getBoolean("torEnabled", false)) {
                torEnable();
            }
        }
    }

    public void torEnable() {
        acceptCookies(false);
        deleteCookies();
        //Make sure that all cookies are really deleted
        if (!CookieManager.getInstance().hasCookies()) {
            if (!OrbotHelper.isOrbotRunning(getApplicationContext()))
                OrbotHelper.requestStartTor(getApplicationContext());
            try {
                WebkitProxy.setProxy(MainActivity.class.getName(), getApplicationContext(), null, "localhost", 8118);
                SharedPreferences.Editor spEdit = sp.edit();
                spEdit.putBoolean("torEnabled", true);
                spEdit.commit();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void torDisable() {
        deleteCookies();
        //Make sure that all cookies are really deleted
        if (!CookieManager.getInstance().hasCookies()) {
            try {
                WebkitProxy.resetProxy(MainActivity.class.getName(), getApplicationContext());
                SharedPreferences.Editor spEdit = sp.edit();
                spEdit.putBoolean("torEnabled", false);
                spEdit.commit();
                acceptCookies(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void acceptCookies(boolean accept) {
        CookieManager.getInstance().setAcceptCookie(accept);
        if (Integer.valueOf(Build.VERSION.SDK_INT) >= 21) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, accept);
        }
    }

    public void deleteCookies() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeAllCookies(null);
        }
        CookieManager.getInstance().removeAllCookie();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }

    }
}
