/*
 * Copyright (C) 2016-2023 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.crdroid.settings.fragments;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import android.app.Activity;
import android.app.AlertDialog; 

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.util.crdroid.SystemRestartUtils;
import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.fragments.misc.SensorBlock;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import lineageos.providers.LineageSettings;

import static org.lineageos.internal.util.DeviceKeysConstants.*;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

@SearchIndexable
public class Miscellaneous extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "Miscellaneous";

    private static final String POCKET_JUDGE = "pocket_judge";
    private static final String SYS_GAMES_SPOOF = "persist.sys.pixelprops.games";
    private static final String SYS_PROP_OPTIONS_PI = "persist.sys.pixelprops.pi";
    private static final String SYS_PHOTOS_SPOOF = "persist.sys.pixelprops.gphotos";
    private static final String SYS_NETFLIX_SPOOF = "persist.sys.pixelprops.netflix";
    
    private static final String KEY_IMPORT_KEYBOX = "import_keybox";
    private static final String KEY_CLEAR_KEYBOX = "clear_keybox";
    private static final String KEYBOX_PATH = "/data/misc/keybox/keybox.xml";

    private static final String SYS_GAMEPROP_ENABLED = "persist.sys.gameprops.enabled";
    private static final String KEY_GAME_PROPS_JSON_FILE_PREFERENCE = "game_props_json_file_preference";
    private static final String KEY_PIF_JSON_FILE_PREFERENCE = "pif_json_file_preference";

    private Preference mPocketJudge;
    private Preference mPropOptionsPi;

    private Preference mImportKeybox;
    private Preference mClearKeybox;

    private Preference mPifJsonFilePreference;

    private Preference mGamePropsJsonFilePreference;
    private Preference mGamePropsSpoof;

    private Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
        addPreferencesFromResource(R.xml.crdroid_settings_misc);
        mGamePropsSpoof = findPreference(SYS_GAMEPROP_ENABLED);
        final PreferenceScreen prefScreen = getPreferenceScreen();
        final Resources res = getResources();

        mClearKeybox = findPreference(KEY_CLEAR_KEYBOX);
        mClearKeybox.setOnPreferenceClickListener(preference -> {
            clearKeybox();
            return true;
        });

        mImportKeybox = findPreference(KEY_IMPORT_KEYBOX);
        mImportKeybox.setOnPreferenceClickListener(preference -> {
            openFileSelector(1003);
            return true;
        });

        Preference convertKeybox = findPreference("convert_keybox");
        if (convertKeybox != null) {
            convertKeybox.setOnPreferenceClickListener(preference -> {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://axionaosp.github.io/#keybox"));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (browserIntent.resolveActivity(requireContext().getPackageManager()) != null) {
                        startActivity(browserIntent);
                    }
                } catch (Exception e) {
                }
                return true;
            });
        }

        mPifJsonFilePreference = findPreference(KEY_PIF_JSON_FILE_PREFERENCE);
        mGamePropsJsonFilePreference = findPreference(KEY_GAME_PROPS_JSON_FILE_PREFERENCE);
        mGamePropsSpoof.setOnPreferenceChangeListener(this);

        mPropOptionsPi = (Preference) findPreference(SYS_PROP_OPTIONS_PI);
        mPropOptionsPi.setOnPreferenceChangeListener(this);

        mPocketJudge = (Preference) prefScreen.findPreference(POCKET_JUDGE);
        boolean mPocketJudgeSupported = res.getBoolean(
                com.android.internal.R.bool.config_pocketModeSupported);
        if (!mPocketJudgeSupported)
            prefScreen.removePreference(mPocketJudge);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
    if (preference == mPifJsonFilePreference) {
        mPifJsonFilePreference.setOnPreferenceClickListener(pref -> {
            openFileSelector(10001);
            return true;
        });
        return true;
    } else if (preference == mGamePropsJsonFilePreference) {
        mGamePropsJsonFilePreference.setOnPreferenceClickListener(pref -> {
            openFileSelector(10002);
            return true;
        });
        return true;
    } else if ("show_pif_properties".equals(preference.getKey())) {
        showPropertiesDialog();
        return true;
    }
    return super.onPreferenceTreeClick(preference); // Default handling
}

   private void openFileSelector(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (requestCode == 10001 || requestCode == 10002 ) {
            intent.setType("application/json");
        } else if (requestCode == 10003) {
            intent.setType("text/xml");
        } else {
            intent.setType("*/*");
        }
        startActivityForResult(intent, requestCode);
   }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                if (requestCode == 10001) {
                    loadPifJson(uri);
                } else if (requestCode == 10002) {
                    loadGameSpoofingJson(uri);
                } else if (requestCode == 10003) {
                    handleKeyboxImport(uri);
            }
        }
    }

    private void showPropertiesDialog() {
        StringBuilder properties = new StringBuilder();
        try {
            JSONObject jsonObject = new JSONObject();
            String[] keys = {
                "persist.sys.pihooks_ID",
                "persist.sys.pihooks_BRAND",
                "persist.sys.pihooks_DEVICE",
                "persist.sys.pihooks_FINGERPRINT",
                "persist.sys.pihooks_MANUFACTURER",
                "persist.sys.pihooks_MODEL",
                "persist.sys.pihooks_PRODUCT",
                "persist.sys.pihooks_SECURITY_PATCH",
                "persist.sys.pihooks_DEVICE_INITIAL_SDK_INT"
            };
            for (String key : keys) {
                String value = SystemProperties.get(key, null);
                if (value != null) {
                    String buildKey = key.replace("persist.sys.pihooks_", "");
                    jsonObject.put(buildKey, value);
                }
            }
            properties.append(jsonObject.toString(4));
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON from properties", e);
            properties.append(getString(R.string.error_loading_properties));
        }
        new AlertDialog.Builder(getContext())
            .setTitle(R.string.show_pif_properties_title)
            .setMessage(properties.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private void loadPifJson(Uri uri) {
        Log.d(TAG, "Loading PIF JSON from URI: " + uri.toString());
        try (InputStream inputStream = getActivity().getContentResolver().openInputStream(uri)) {
            if (inputStream != null) {
                String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                Log.d(TAG, "PIF JSON data: " + json);
                JSONObject jsonObject = new JSONObject(json);
                for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                    String key = it.next();
                    String value = jsonObject.getString(key);
                    Log.d(TAG, "Setting PIF property: persist.sys.pihooks_" + key + " = " + value);
                    SystemProperties.set("persist.sys.pihooks_" + key, value);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading PIF JSON or setting properties", e);
        }
        mHandler.postDelayed(() -> {
            SystemRestartUtils.showSystemRestartDialog(getContext());
        }, 1250);
    }

    private void loadGameSpoofingJson(Uri uri) {
        Log.d(TAG, "Loading Game Props JSON from URI: " + uri.toString());
        try (InputStream inputStream = getActivity().getContentResolver().openInputStream(uri)) {
            if (inputStream != null) {
                String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                Log.d(TAG, "Game Props JSON data: " + json);
                JSONObject jsonObject = new JSONObject(json);
                for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                    String key = it.next();
                    if (key.startsWith("PACKAGES_") && !key.endsWith("_DEVICE")) {
                        String deviceKey = key + "_DEVICE";
                        if (jsonObject.has(deviceKey)) {
                            JSONObject deviceProps = jsonObject.getJSONObject(deviceKey);
                            JSONArray packages = jsonObject.getJSONArray(key);
                            for (int i = 0; i < packages.length(); i++) {
                                String packageName = packages.getString(i);
                                Log.d(TAG, "Spoofing package: " + packageName);
                                setGameProps(packageName, deviceProps);
                            }
                        }            
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading Game Props JSON or setting properties", e);
        }
        mHandler.postDelayed(() -> {
            SystemRestartUtils.showSystemRestartDialog(getContext());
        }, 1250);
    }

    private void setGameProps(String packageName, JSONObject deviceProps) {
        try {
            for (Iterator<String> it = deviceProps.keys(); it.hasNext(); ) {
                String key = it.next();
                String value = deviceProps.getString(key);
                String systemPropertyKey = "persist.sys.gameprops." + packageName + "." + key;
                SystemProperties.set(systemPropertyKey, value);
                Log.d(TAG, "Set system property: " + systemPropertyKey + " = " + value);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing device properties", e);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mGamePropsSpoof || preference == mPropOptionsPi) {
                    SystemRestartUtils.showSystemRestartDialog(getContext());
            return true;
        }
        return false;
    }

    private void handleKeyboxImport(Uri uri) {
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(in);
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();
            if (root == null || !"AndroidAttestation".equals(root.getNodeName())) {
                Log.e(TAG, "Invalid root element. Expected <AndroidAttestation>");
                showToast(R.string.import_failed);
                return;
            }

            NodeList keyboxes = doc.getElementsByTagName("Keybox");
            if (keyboxes.getLength() == 0) {
                Log.e(TAG, "No <Keybox> element found in XML.");
                showToast(R.string.import_failed);
                return;
            }

            JSONObject keyboxJson = new JSONObject();

            for (int i = 0; i < keyboxes.getLength(); i++) {
                Element keyboxElement = (Element) keyboxes.item(i);
                NodeList keys = keyboxElement.getElementsByTagName("Key");

                if (keys.getLength() == 0) {
                    Log.w(TAG, "No <Key> entries in <Keybox>. Skipping.");
                    continue;
                }

                for (int j = 0; j < keys.getLength(); j++) {
                    Element keyElement = (Element) keys.item(j);
                    String algorithm = keyElement.getAttribute("algorithm").toUpperCase();
                    if (TextUtils.isEmpty(algorithm)) {
                        Log.w(TAG, "Missing 'algorithm' attribute in <Key>. Skipping.");
                        continue;
                    }

                    if (algorithm.equals("ECDSA")) algorithm = "EC";

                    Element privKeyElem = (Element) keyElement.getElementsByTagName("PrivateKey").item(0);
                    if (privKeyElem == null) {
                        Log.w(TAG, "No <PrivateKey> found for algorithm " + algorithm + ". Skipping.");
                        continue;
                    }

                    String privKeyRaw = getRawText(privKeyElem);
                    String privKey = extractBase64FromPEM(privKeyRaw);
                    if (TextUtils.isEmpty(privKey)) {
                        Log.w(TAG, "Empty private key for " + algorithm + ". Skipping.");
                        continue;
                    }
                    keyboxJson.put(algorithm + ".PRIV", privKey);

                    NodeList certList = keyElement.getElementsByTagName("Certificate");
                    for (int k = 0; k < certList.getLength(); k++) {
                        Element certElem = (Element) certList.item(k);
                        String certRaw = getRawText(certElem);
                        String cert = extractBase64FromPEM(certRaw);
                        if (!TextUtils.isEmpty(cert)) {
                            keyboxJson.put(algorithm + ".CERT_" + (k + 1), cert);
                        } else {
                            Log.w(TAG, "Empty certificate #" + (k + 1) + " for " + algorithm);
                        }
                    }
                }
            }

            if (keyboxJson.length() == 0) {
                Log.e(TAG, "Parsed keybox is empty. Import failed.");
                showToast(R.string.import_failed);
                return;
            }

            Settings.System.putString(requireContext().getContentResolver(),
                    "custom_keybox_data", keyboxJson.toString());

            showToast(R.string.import_success);
            SystemRestartUtils.showSystemRestartDialog(getContext());

        } catch (Exception e) {
            Log.e(TAG, "Keybox import failed", e);
            showToast(R.string.import_failed);
        }
    }

    private String getRawText(Element element) {
        StringBuilder builder = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                builder.append(node.getNodeValue());
            }
        }
        return builder.toString().trim();
    }

    private String extractBase64FromPEM(String pem) {
        return pem.replaceAll("-----BEGIN [^-]+-----", "")
                  .replaceAll("-----END [^-]+-----", "")
                  .replaceAll("[\\r\\n\\s]+", "");
    }

    private void showToast(int resId) {
        getActivity().runOnUiThread(() -> 
            Toast.makeText(getContext(), resId, Toast.LENGTH_SHORT).show()
        );
    }

    private void clearKeybox() {
        try {
            Settings.System.putString(requireContext().getContentResolver(), "custom_keybox_data", null);
            showToast(R.string.clear_success);
            SystemRestartUtils.showSystemRestartDialog(getContext());
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear keybox", e);
            showToast(R.string.clear_failed);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.crdroid_settings_misc) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    final Resources res = context.getResources();

                    boolean mPocketJudgeSupported = res.getBoolean(
                            com.android.internal.R.bool.config_pocketModeSupported);
                    if (!mPocketJudgeSupported)
                        keys.add(POCKET_JUDGE);

                    return keys;
                }
            };
}
