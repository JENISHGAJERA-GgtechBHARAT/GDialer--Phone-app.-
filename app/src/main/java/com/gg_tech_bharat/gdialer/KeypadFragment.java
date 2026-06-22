package com.gg_tech_bharat.gdialer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class KeypadFragment extends Fragment implements View.OnClickListener {

    private TextView tvDialedNumber, tvCreateContact, tvAddToContact;
    private LinearLayout layoutKeypadActions;
    private ImageButton btnBackspace, btnCall, btnCallSim2, btnVideoCall, btnKeypadMenu;
    private RecyclerView rvSuggestions;
    private ContactAdapter adapter;
    private final StringBuilder dialedDigits = new StringBuilder();

    private List<PhoneAccountHandle> simHandles = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_keypad, container, false);

        tvDialedNumber = view.findViewById(R.id.tvDialedNumber);
        tvCreateContact = view.findViewById(R.id.tvCreateContact);
        tvAddToContact = view.findViewById(R.id.tvAddToContact);
        layoutKeypadActions = view.findViewById(R.id.layoutKeypadActions);
        
        btnBackspace = view.findViewById(R.id.btnBackspace);
        btnCall = view.findViewById(R.id.btnCall);
        btnCallSim2 = view.findViewById(R.id.btnCallSim2);
        btnVideoCall = view.findViewById(R.id.btnVideoCall);
        btnKeypadMenu = view.findViewById(R.id.btnKeypadMenu);
        rvSuggestions = view.findViewById(R.id.rvT9Suggestions);

        setupSimButtons();

        rvSuggestions.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ContactAdapter(requireContext());
        rvSuggestions.setAdapter(adapter);

        // ADD SWIPE SUPPORT TO KEYPAD SUGGESTIONS
        new androidx.recyclerview.widget.ItemTouchHelper(new SwipeToCallMessageCallback(requireContext(), new SwipeToCallMessageCallback.SwipeActionListener() {
            @Override public void onCallAction(int p) {
                Context context = getContext();
                if (context == null || adapter == null) return;
                ContactModel c = adapter.getContactAt(p);
                if (c != null) Utils.makePhoneCall(context, c.getNumber());
                adapter.notifyItemChanged(p);
            }
            @Override public void onMessageAction(int p) {
                Context context = getContext();
                if (context == null || adapter == null) return;
                ContactModel c = adapter.getContactAt(p);
                if (c != null) Utils.sendSMS(context, c.getNumber(), "");
                adapter.notifyItemChanged(p);
            }
        })).attachToRecyclerView(rvSuggestions);

        if (btnKeypadMenu != null) btnKeypadMenu.setOnClickListener(this::showKeypadPopupMenu);

        int[] keys = {R.id.tvKey1, R.id.tvKey2, R.id.tvKey3, R.id.tvKey4, R.id.tvKey5, R.id.tvKey6, R.id.tvKey7, R.id.tvKey8, R.id.tvKey9, R.id.tvKeyStar, R.id.tvKey0, R.id.tvKeyHash};
        for (int id : keys) {
            View b = view.findViewById(id);
            if (b != null) {
                View p = (View) b.getParent(); // LinearLayout
                View gp = (View) p.getParent(); // FrameLayout
                
                gp.setOnClickListener(this);
                if (id == R.id.tvKey1) gp.setOnLongClickListener(v -> { Utils.triggerHaptic(v); dialVoicemail(); return true; });
                else if (id == R.id.tvKey0) gp.setOnLongClickListener(v -> { Utils.triggerHaptic(v); dialedDigits.append("+"); tvDialedNumber.setText(dialedDigits.toString()); return true; });
                else if (id != R.id.tvKeyStar && id != R.id.tvKeyHash) {
                    final String d = ((TextView) b).getText().toString();
                    gp.setOnLongClickListener(v -> { Utils.triggerHaptic(v); handleSpeedDialLongClick(d); return true; });
                }
            }
        }

        btnBackspace.setOnClickListener(v -> deleteLastDigit());
        btnBackspace.setOnLongClickListener(v -> { clearDigits(); return true; });
        btnCall.setOnClickListener(v -> makeCall(0, VideoProfile.STATE_AUDIO_ONLY));
        btnCallSim2.setOnClickListener(v -> makeCall(1, VideoProfile.STATE_AUDIO_ONLY));
        
        if (btnVideoCall != null) {
            btnVideoCall.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                makeCall(0, VideoProfile.STATE_BIDIRECTIONAL);
            });
        }

        tvCreateContact.setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            if (dialedDigits.length() > 0) {
                startActivity(new Intent(requireContext(), EditContactActivity.class).putExtra("EXTRA_NUMBER", dialedDigits.toString()));
                clearDigits();
            }
        });

        tvAddToContact.setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            if (dialedDigits.length() > 0) {
                startActivity(new Intent(requireContext(), EditContactActivity.class).putExtra("EXTRA_NUMBER", dialedDigits.toString()));
                clearDigits();
            }
        });

        tvDialedNumber.setOnLongClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            PopupMenu popup = new PopupMenu(requireContext(), v);
            
            if (clipboard.hasPrimaryClip()) {
                popup.getMenu().add(0, 1, 0, "Paste");
            }
            if (dialedDigits.length() > 0) {
                popup.getMenu().add(0, 2, 1, "Copy");
            }

            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    ClipData clip = clipboard.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        CharSequence text = clip.getItemAt(0).getText();
                        if (text != null) {
                            String filtered = text.toString().replaceAll("[^0-9*#+]", "");
                            dialedDigits.append(filtered);
                            tvDialedNumber.setText(dialedDigits.toString());
                        }
                    }
                    return true;
                } else if (item.getItemId() == 2) {
                    ClipData clip = ClipData.newPlainText("Dialed Number", dialedDigits.toString());
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(requireContext(), "Number copied to clipboard", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
            popup.show();
            return true;
        });

        tvDialedNumber.addTextChangedListener(new TextWatcher() {
            private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            private Runnable r;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String input = s.toString();
                btnBackspace.setVisibility(input.isEmpty() ? View.INVISIBLE : View.VISIBLE);
                layoutKeypadActions.setVisibility(input.isEmpty() ? View.INVISIBLE : View.VISIBLE);
                if (r != null) h.removeCallbacks(r);
                r = () -> updateT9Suggestions(input);
                h.postDelayed(r, 200);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        return view;
    }

    private void showKeypadPopupMenu(View v) {
        Utils.triggerHaptic(v);
        PopupMenu p = new PopupMenu(requireContext(), v);
        p.getMenu().add("Speed dial");
        p.getMenu().add("Settings");
        p.setOnMenuItemClickListener(item -> {
            if (item.getTitle() == null) return false;
            String title = item.getTitle().toString();
            if ("Speed dial".equals(title)) startActivity(new Intent(requireContext(), SpeedDialActivity.class));
            else if ("Settings".equals(title)) startActivity(new Intent(requireContext(), SettingsActivity.class));
            return true;
        });
        p.show();
    }

    private void dialVoicemail() { Utils.makePhoneCall(requireContext(), "*86"); }

    private void handleSpeedDialLongClick(String digit) {
        String assignment = requireContext().getSharedPreferences("SpeedDialPrefs", Context.MODE_PRIVATE).getString("key_" + digit, null);
        if (assignment != null) {
            String[] parts = assignment.split("\\|");
            if (parts.length > 1) Utils.makePhoneCall(requireContext(), parts[1]);
        } else {
            new AlertDialog.Builder(requireContext()).setTitle("Assign Speed Dial").setMessage("No contact for " + digit).setPositiveButton("Assign", (d, w) -> startActivity(new Intent(requireContext(), SpeedDialActivity.class))).show();
        }
    }

    @Override
    public void onClick(View v) {
        Utils.triggerHaptic(v);
        int[] ids = {R.id.tvKey1, R.id.tvKey2, R.id.tvKey3, R.id.tvKey4, R.id.tvKey5, R.id.tvKey6, R.id.tvKey7, R.id.tvKey8, R.id.tvKey9, R.id.tvKeyStar, R.id.tvKey0, R.id.tvKeyHash};
        for (int id : ids) {
            TextView tv = v.findViewById(id);
            if (tv != null) {
                dialedDigits.append(tv.getText().toString());
                tvDialedNumber.setText(dialedDigits.toString());
                return;
            }
        }
    }

    private void deleteLastDigit() {
        if (dialedDigits.length() > 0) { dialedDigits.deleteCharAt(dialedDigits.length() - 1); tvDialedNumber.setText(dialedDigits.toString()); }
    }

    private void clearDigits() { dialedDigits.setLength(0); tvDialedNumber.setText(""); }

    public void setDialedNumber(String number) {
        if (number == null) return;
        dialedDigits.setLength(0);
        dialedDigits.append(number.replaceAll("[^0-9*#+]", ""));
        if (tvDialedNumber != null) tvDialedNumber.setText(dialedDigits.toString());
    }

    private void setupSimButtons() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            TelecomManager tm = (TelecomManager) requireContext().getSystemService(Context.TELECOM_SERVICE);
            if (tm != null) {
                simHandles = tm.getCallCapablePhoneAccounts();
                if (btnCallSim2 != null) btnCallSim2.setVisibility(simHandles.size() >= 2 ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void makeCall(int simIndex, int videoState) {
        String num = dialedDigits.toString();
        if (num.isEmpty()) return;
        PhoneAccountHandle h = (simIndex < simHandles.size()) ? simHandles.get(simIndex) : null;
        Utils.makePhoneCall(requireContext(), num, h, videoState);
    }

    private void updateT9Suggestions(String digits) {
        if (digits.isEmpty()) { adapter.setContacts(new ArrayList<>()); return; }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<ContactModel> results = new ArrayList<>();
            List<ContactModel> cached = ContactCache.getCachedContacts();
            String q = digits.replaceAll("[^0-9]", "");
            for (ContactModel c : cached) {
                if (matchesT9(c.getNormalizedName(), q) || c.getNormalizedNumber().contains(q)) results.add(c);
            }
            if (getActivity() != null) getActivity().runOnUiThread(() -> adapter.setContacts(results));
        });
    }

    private boolean matchesT9(String name, String digits) {
        if (name == null || name.isEmpty() || digits == null || digits.isEmpty()) return false;
        int dIdx = 0;
        for (int i = 0; i < name.length() && dIdx < digits.length(); i++) {
            if (getT9Digit(name.charAt(i)) == digits.charAt(dIdx)) dIdx++;
            else dIdx = 0;
        }
        return dIdx == digits.length();
    }

    private char getT9Digit(char c) {
        switch (Character.toLowerCase(c)) {
            case 'a': case 'b': case 'c': return '2';
            case 'd': case 'e': case 'f': return '3';
            case 'g': case 'h': case 'i': return '4';
            case 'j': case 'k': case 'l': return '5';
            case 'm': case 'n': case 'o': return '6';
            case 'p': case 'q': case 'r': case 's': return '7';
            case 't': case 'u': case 'v': return '8';
            case 'w': case 'x': case 'y': case 'z': return '9';
            default: return '0';
        }
    }
}
