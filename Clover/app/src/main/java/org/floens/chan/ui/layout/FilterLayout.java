/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.layout;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.manager.FilterEngine;
import org.floens.chan.core.manager.FilterType;
import org.floens.chan.core.model.Filter;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.Sites;
import org.floens.chan.ui.controller.FiltersController;
import org.floens.chan.ui.dialog.ColorPickerView;
import org.floens.chan.ui.drawable.DropdownArrowDrawable;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static org.floens.chan.Chan.getGraph;
import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;
import static org.floens.chan.utils.AndroidUtils.getString;

public class FilterLayout extends LinearLayout implements View.OnClickListener {
    private TextView typeText;
    private TextView boardsSelector;
    private boolean patternContainerErrorShowing = false;
    private TextView pattern;
    private TextView patternPreview;
    private TextView patternPreviewStatus;
    private CheckBox enabled;
    private ImageView help;
    private TextView actionText;
    private LinearLayout colorContainer;
    private View colorPreview;

    @Inject
    BoardManager boardManager;

    @Inject
    FilterEngine filterEngine;

    private FilterLayoutCallback callback;
    private Filter filter;

    private List<FilterEngine.SiteIdBoardCode> appliedBoards = new ArrayList<>();

    public FilterLayout(Context context) {
        super(context);
    }

    public FilterLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FilterLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        getGraph().inject(this);

        typeText = (TextView) findViewById(R.id.type);
        boardsSelector = (TextView) findViewById(R.id.boards);
        actionText = (TextView) findViewById(R.id.action);
        pattern = (TextView) findViewById(R.id.pattern);
        pattern.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter.pattern = s.toString();
                updateFilterValidity();
                updatePatternPreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        patternPreview = (TextView) findViewById(R.id.pattern_preview);
        patternPreview.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePatternPreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        patternPreviewStatus = (TextView) findViewById(R.id.pattern_preview_status);
        enabled = (CheckBox) findViewById(R.id.enabled);
        help = (ImageView) findViewById(R.id.help);
        theme().helpDrawable.apply(help);
        help.setOnClickListener(this);
        colorContainer = (LinearLayout) findViewById(R.id.color_container);
        colorContainer.setOnClickListener(this);
        colorPreview = findViewById(R.id.color_preview);

        typeText.setOnClickListener(this);
        typeText.setCompoundDrawablesWithIntrinsicBounds(null, null, new DropdownArrowDrawable(dp(12), dp(12), true,
                getAttrColor(getContext(), R.attr.dropdown_dark_color), getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color)), null);

        boardsSelector.setOnClickListener(this);
        boardsSelector.setCompoundDrawablesWithIntrinsicBounds(null, null, new DropdownArrowDrawable(dp(12), dp(12), true,
                getAttrColor(getContext(), R.attr.dropdown_dark_color), getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color)), null);

        actionText.setOnClickListener(this);
        actionText.setCompoundDrawablesWithIntrinsicBounds(null, null, new DropdownArrowDrawable(dp(12), dp(12), true,
                getAttrColor(getContext(), R.attr.dropdown_dark_color), getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color)), null);
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
        appliedBoards.clear();
        appliedBoards.addAll(filterEngine.getBoardsForFilter(filter));

        pattern.setText(filter.pattern);

        updateFilterValidity();
        updateCheckboxes();
        updateFilterType();
        updateFilterAction();
        updateBoardsSummary();
        updatePatternPreview();
    }

    public void setCallback(FilterLayoutCallback callback) {
        this.callback = callback;
    }

    public Filter getFilter() {
        filter.enabled = enabled.isChecked();

        filterEngine.saveBoardsToFilter(appliedBoards, filter);

        return filter;
    }

    @Override
    public void onClick(View v) {
        if (v == typeText) {
            @SuppressWarnings("unchecked")
            final SelectLayout<FilterType> selectLayout = (SelectLayout<FilterType>) LayoutInflater.from(getContext()).inflate(R.layout.layout_select, null);

            List<SelectLayout.SelectItem<FilterType>> items = new ArrayList<>();
            for (FilterType filterType : FilterType.values()) {
                String name = FiltersController.filterTypeName(filterType);
                String description = getString(filterType.isRegex ? R.string.filter_type_regex_matching : R.string.filter_type_string_matching);
                boolean checked = filter.hasFilter(filterType);

                items.add(new SelectLayout.SelectItem<>(
                        filterType, filterType.flag, name, description, name, checked
                ));
            }

            selectLayout.setItems(items);

            new AlertDialog.Builder(getContext())
                    .setView(selectLayout)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            List<SelectLayout.SelectItem<FilterType>> items = selectLayout.getItems();
                            int flags = 0;
                            for (SelectLayout.SelectItem<FilterType> item : items) {
                                if (item.checked) {
                                    flags |= item.item.flag;
                                }
                            }

                            filter.type = flags;
                            updateFilterType();
                            updatePatternPreview();
                        }
                    })
                    .show();
        } else if (v == boardsSelector) {
            // TODO(multi-site): fix this crap.
            // we need a new proper recyclerview layout where you can individually select each site and board combination
            // and if you don't select anything, it becomes a global filter.

            final LinearLayout selectLayout = (LinearLayout) LayoutInflater.from(getContext())
                    .inflate(R.layout.layout_site_board_select, null);

            final Spinner spinner = (Spinner) selectLayout.findViewById(R.id.spinner);

            final List<? extends Site> allSites = Sites.ALL_SITES;

            final Site[] selectedSite = {allSites.get(0)};

            List<String> allSitesNames = new ArrayList<>(allSites.size());
            for (int i = 0; i < allSites.size(); i++) {
                Site site = allSites.get(i);
                allSitesNames.add(site.name());
            }

            SpinnerAdapter adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, allSitesNames);
            spinner.setAdapter(adapter);

            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Site site = allSites.get(position);
                    selectedSite[0] = site;
                    Logger.test(site.name());
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

            final EditText editText = (EditText) selectLayout.findViewById(R.id.boards);

            String text = "";
            for (int i = 0; i < appliedBoards.size(); i++) {
                FilterEngine.SiteIdBoardCode siteIdBoardCode = appliedBoards.get(i);
                text += siteIdBoardCode.boardCode;
                if (i < appliedBoards.size() - 1) {
                    text += ",";
                }
            }

            editText.setText(text);

            new AlertDialog.Builder(getContext())
                    .setView(selectLayout)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Site site = selectedSite[0];

                            appliedBoards.clear();

                            String[] codes = editText.getText().toString().split(",");
                            if (codes.length == 0) {
                                filter.allBoards = true;
                            } else {
                                filter.allBoards = false;
                                for (String code : codes) {
                                    appliedBoards.add(FilterEngine.SiteIdBoardCode.fromSiteIdBoardCode(
                                            site.id(), code
                                    ));
                                }
                            }

                            updateBoardsSummary();
                        }
                    })
                    .show();
        } else if (v == actionText) {
            List<FloatingMenuItem> menuItems = new ArrayList<>(6);

            for (FilterEngine.FilterAction action : FilterEngine.FilterAction.values()) {
                menuItems.add(new FloatingMenuItem(action, FiltersController.actionName(action)));
            }

            FloatingMenu menu = new FloatingMenu(v.getContext());
            menu.setAnchor(v, Gravity.LEFT, -dp(5), -dp(5));
            menu.setPopupWidth(dp(150));
            menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
                @Override
                public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                    FilterEngine.FilterAction action = (FilterEngine.FilterAction) item.getId();
                    filter.action = action.id;
                    updateFilterAction();
                }

                @Override
                public void onFloatingMenuDismissed(FloatingMenu menu) {
                }
            });
            menu.setItems(menuItems);
            menu.show();
        } else if (v == help) {
            SpannableStringBuilder message = (SpannableStringBuilder) Html.fromHtml(getString(R.string.filter_help));
            TypefaceSpan[] typefaceSpans = message.getSpans(0, message.length(), TypefaceSpan.class);
            for (TypefaceSpan span : typefaceSpans) {
                if (span.getFamily().equals("monospace")) {
                    int start = message.getSpanStart(span);
                    int end = message.getSpanEnd(span);
                    message.setSpan(new BackgroundColorSpan(0x22000000), start, end, 0);
                }
            }

            StyleSpan[] styleSpans = message.getSpans(0, message.length(), StyleSpan.class);
            for (StyleSpan span : styleSpans) {
                if (span.getStyle() == Typeface.ITALIC) {
                    int start = message.getSpanStart(span);
                    int end = message.getSpanEnd(span);
                    message.setSpan(new BackgroundColorSpan(0x22000000), start, end, 0);
                }
            }

            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.filter_help_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        } else if (v == colorContainer) {
            final ColorPickerView colorPickerView = new ColorPickerView(getContext());
            colorPickerView.setColor(filter.color);

            AlertDialog dialog = new AlertDialog.Builder(getContext())
                    .setTitle(R.string.filter_color_pick)
                    .setView(colorPickerView)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            filter.color = colorPickerView.getColor();
                            updateFilterAction();
                        }
                    })
                    .show();
            dialog.getWindow().setLayout(dp(300), dp(300));
        }
    }

    private void updateFilterValidity() {
        boolean valid = !TextUtils.isEmpty(filter.pattern) && filterEngine.compile(filter.pattern) != null;

        if (valid != patternContainerErrorShowing) {
            patternContainerErrorShowing = valid;
            pattern.setError(valid ? null : getString(R.string.filter_invalid_pattern));
        }

        if (callback != null) {
            callback.setSaveButtonEnabled(valid);
        }
    }

    private void updateBoardsSummary() {
        String text = getString(R.string.filter_boards) + " (";
        if (filter.allBoards) {
            text += getString(R.string.filter_all);
        } else {
            text += String.valueOf(appliedBoards.size());
        }
        text += ")";
        boardsSelector.setText(text);
    }

    private void updateCheckboxes() {
        enabled.setChecked(filter.enabled);
    }

    private void updateFilterAction() {
        FilterEngine.FilterAction action = FilterEngine.FilterAction.forId(filter.action);
        actionText.setText(FiltersController.actionName(action));
        colorContainer.setVisibility(action == FilterEngine.FilterAction.COLOR ? VISIBLE : GONE);
        if (filter.color == 0) {
            filter.color = 0xffff0000;
        }
        colorPreview.setBackgroundColor(filter.color);
    }

    private void updateFilterType() {
        int types = FilterType.forFlags(filter.type).size();
        String text = getString(R.string.filter_types) + " (" + types + ")";
        typeText.setText(text);
    }

    private void updatePatternPreview() {
        String text = patternPreview.getText().toString();
        boolean matches = text.length() > 0 && filterEngine.matches(filter, true, text, true);
        patternPreviewStatus.setText(matches ? R.string.filter_matches : R.string.filter_no_matches);
    }

    public interface FilterLayoutCallback {
        void setSaveButtonEnabled(boolean enabled);
    }
}
