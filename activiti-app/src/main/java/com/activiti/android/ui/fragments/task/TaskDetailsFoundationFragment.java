/*
 *  Copyright (C) 2005-2016 Alfresco Software Limited.
 *
 *  This file is part of Alfresco Activiti Mobile for Android.
 *
 *  Alfresco Activiti Mobile for Android is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Alfresco Activiti Mobile for Android is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

package com.activiti.android.ui.fragments.task;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.activiti.android.app.ActivitiVersionNumber;
import com.activiti.android.app.R;
import com.activiti.android.app.activity.MainActivity;
import com.activiti.android.app.fragments.comment.CommentsFragment;
import com.activiti.android.app.fragments.task.TaskDetailsFragment;
import com.activiti.android.app.fragments.task.TaskFormFragment;
import com.activiti.android.app.fragments.task.TasksFragment;
import com.activiti.android.platform.EventBusManager;
import com.activiti.android.platform.account.ActivitiAccountManager;
import com.activiti.android.platform.event.CompleteTaskEvent;
import com.activiti.android.platform.integration.analytics.AnalyticsHelper;
import com.activiti.android.platform.integration.analytics.AnalyticsManager;
import com.activiti.android.platform.provider.transfer.ContentTransferManager;
import com.activiti.android.platform.rendition.RenditionManager;
import com.activiti.android.platform.utils.BundleUtils;
import com.activiti.android.sdk.ActivitiSession;
import com.activiti.android.sdk.model.runtime.ParcelTask;
import com.activiti.android.ui.fragments.FragmentDisplayer;
import com.activiti.android.ui.fragments.common.AbstractDetailsFragment;
import com.activiti.android.ui.fragments.common.ListingModeFragment;
import com.activiti.android.ui.fragments.form.EditTextDialogFragment;
import com.activiti.android.ui.fragments.form.picker.ActivitiUserPickerFragment;
import com.activiti.android.ui.fragments.form.picker.DatePickerFragment;
import com.activiti.android.ui.fragments.form.picker.DatePickerFragment.onPickDateFragment;
import com.activiti.android.ui.fragments.form.picker.UserPickerFragment;
import com.activiti.android.ui.fragments.task.create.CreateStandaloneTaskDialogFragment;
import com.activiti.android.ui.fragments.task.form.AttachFormTaskDialogFragment;
import com.activiti.android.ui.holder.HolderUtils;
import com.activiti.android.ui.holder.TwoLinesViewHolder;
import com.activiti.android.ui.utils.DisplayUtils;
import com.activiti.android.ui.utils.Formatter;
import com.activiti.android.ui.utils.UIUtils;
import com.activiti.client.api.model.common.ResultList;
import com.activiti.client.api.model.editor.ModelRepresentation;
import com.activiti.client.api.model.editor.form.FormDefinitionRepresentation;
import com.activiti.client.api.model.idm.LightUserRepresentation;
import com.activiti.client.api.model.runtime.ProcessInstanceRepresentation;
import com.activiti.client.api.model.runtime.RelatedContentRepresentation;
import com.activiti.client.api.model.runtime.TaskRepresentation;
import com.activiti.client.api.model.runtime.request.AssignTaskRepresentation;
import com.activiti.client.api.model.runtime.request.AttachFormTaskRepresentation;
import com.activiti.client.api.model.runtime.request.InvolveTaskRepresentation;
import com.activiti.client.api.model.runtime.request.UpdateTaskRepresentation;
import com.daimajia.swipe.SwipeLayout;

/**
 * Created by jpascal on 07/03/2015.
 */
public class TaskDetailsFoundationFragment extends AbstractDetailsFragment
        implements onPickDateFragment, UserPickerFragment.onPickAuthorityFragment,
        EditTextDialogFragment.onEditTextFragment, ActivitiUserPickerFragment.UserEmailPickerCallback
{
    public static final String TAG = TaskDetailsFoundationFragment.class.getName();

    public static final String ARGUMENT_TASK_ID = "userId";

    public static final String ARGUMENT_TASK = "task";

    private static final String DUE_DATE = "dueDate";

    private static final int EDIT_DESCRIPTION = 1;

    protected ModelRepresentation formDefinitionModel;

    protected String formModelName;

    protected RenditionManager rendition;

    protected List<LightUserRepresentation> people = new ArrayList<>(0);

    protected boolean hasPeopleLoaded = false, hasCheckList = false;

    protected View.OnClickListener onProcessListener, onParentTaskListener;

    protected TwoLinesViewHolder assigneeHolder, dueDateHolder, descriptionHolder, formHolder;

    protected Date dueAt;

    /** real value of formkey (variable as change is possible) */
    protected String formKey;

    protected String description;

    protected LightUserRepresentation assignee;

    protected ParcelTask task;

    protected List<TaskRepresentation> checkListTasks = new ArrayList<>(0);

    // ///////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS & HELPERS
    // ///////////////////////////////////////////////////////////////////////////
    public TaskDetailsFoundationFragment()
    {
        super();
    }

    public static TaskDetailsFoundationFragment newInstanceByTemplate(Bundle b)
    {
        TaskDetailsFoundationFragment cbf = new TaskDetailsFoundationFragment();
        cbf.setArguments(b);
        return cbf;
    }

    // ///////////////////////////////////////////////////////////////////////////
    // LIFECYCLE
    // ///////////////////////////////////////////////////////////////////////////
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        rendition = new RenditionManager(getActivity(), ActivitiSession.getInstance());
        setRootView(inflater.inflate(R.layout.fr_task_details, container, false));
        return getRootView();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        displayLoading();

        if (getArguments() != null)
        {
            task = getArguments().getParcelable(ARGUMENT_TASK);
            if (task == null)
            {
                taskId = BundleUtils.getString(getArguments(), ARGUMENT_TASK_ID);
            }
            else
            {
                taskId = task.id;
            }
        }

        // Retrieve Information
        getAPI().getTaskService().getById(taskId, new Callback<TaskRepresentation>()
        {
            @Override
            public void onResponse(Call<TaskRepresentation> call, Response<TaskRepresentation> response)
            {
                if (!response.isSuccessful())
                {
                    onFailure(call, new Exception(response.message()));
                    return;
                }
                if (!response.isSuccessful())
                {
                    onFailure(call, new Exception(response.message()));
                }
                taskRepresentation = response.body();
                displayInfo();
                people = response.body().getInvolvedPeople();
                hasPeopleLoaded = true;

                requestExtraInfo();

                displayCards();

                commentFragment = (CommentsFragment) CommentsFragment.with(getActivity()).readonly(isEnded)
                        .taskId(taskId).createFragment();
                FragmentDisplayer.with(getActivity()).back(false).animate(null).replace(commentFragment)
                        .into(R.id.right_drawer);

                UIUtils.setTitle(getActivity(), taskRepresentation.getName(), getString(R.string.task_title_details),
                        true);
            }

            @Override
            public void onFailure(Call<TaskRepresentation> call, Throwable error)
            {
                displayError();
                Snackbar.make(getActivity().findViewById(R.id.left_panel), error.getMessage(), Snackbar.LENGTH_SHORT)
                        .show();
            }
        });
    }

    @Override
    public void onResume()
    {
        super.onResume();
        UIUtils.setTitle(getActivity(), task != null ? task.name : null, getString(R.string.task_title_details), true);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData)
    {
        if (requestCode == ContentTransferManager.PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK)
        {
            ContentTransferManager.prepareTransfer(resultData, this, taskId, ContentTransferManager.TYPE_TASK_ID);
        }
    }

    // ///////////////////////////////////////////////////////////////////////////
    // API REQUEST
    // ///////////////////////////////////////////////////////////////////////////
    private void refreshInfo()
    {
        // Retrieve Information
        getAPI().getTaskService().getById(taskId, new Callback<TaskRepresentation>()
        {
            @Override
            public void onResponse(Call<TaskRepresentation> call, Response<TaskRepresentation> response)
            {
                if (!response.isSuccessful())
                {
                    onFailure(call, new Exception(response.message()));
                    return;
                }
                taskRepresentation = response.body();
                displayInfo();
                people = response.body().getInvolvedPeople();
                hasPeopleLoaded = true;

                UIUtils.setTitle(getActivity(), taskRepresentation.getName(), getString(R.string.task_title_details),
                        true);

                requestExtraInfo();

                displayCards();
            }

            @Override
            public void onFailure(Call<TaskRepresentation> call, Throwable error)
            {
                displayError();
                Snackbar.make(getActivity().findViewById(R.id.left_panel), error.getMessage(), Snackbar.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void requestExtraInfo()
    {
        if (taskRepresentation == null) { return; }

        // Retrieve Process Info
        if (taskRepresentation.getProcessInstanceId() != null)
        {
            if (processInstanceRepresentation != null)
            {
                displayProcessProperty(processInstanceRepresentation);
            }
            else
            {
                displayProcessProperty(null);

                getAPI().getProcessService().getById(taskRepresentation.getProcessInstanceId(),
                        new Callback<ProcessInstanceRepresentation>()
                        {
                            @Override
                            public void onResponse(Call<ProcessInstanceRepresentation> call,
                                    Response<ProcessInstanceRepresentation> response)
                            {
                                if (!response.isSuccessful())
                                {
                                    onFailure(call, new Exception(response.message()));
                                    return;
                                }
                                processInstanceRepresentation = response.body();
                                displayProcessProperty(response.body());
                            }

                            @Override
                            public void onFailure(Call<ProcessInstanceRepresentation> call, Throwable error)
                            {

                            }
                        });
            }
        }

        if (taskRepresentation.getParentTaskId() != null)
        {
            displayParentTaskProperty();
        }

        // Retrieve FormModel Info
        formKey = taskRepresentation.getFormKey();
        retrieveForm();

        // Retrieve Contents
        getAPI().getTaskService().getAttachments(taskId, new Callback<ResultList<RelatedContentRepresentation>>()
        {
            @Override
            public void onResponse(Call<ResultList<RelatedContentRepresentation>> call,
                    Response<ResultList<RelatedContentRepresentation>> response)
            {
                if (!response.isSuccessful())
                {
                    onFailure(call, new Exception(response.message()));
                    return;
                }
                relatedContentRepresentations = response.body().getList();
                hasContentLoaded = true;
                displayCards();
            }

            @Override
            public void onFailure(Call<ResultList<RelatedContentRepresentation>> call, Throwable error)
            {
                displayContents(null);
            }
        });

        // Retrieve CheckList
        if (getVersionNumber() < ActivitiVersionNumber.VERSION_1_3_0)
        {
            hasCheckList = true;
            return;
        }
        requestChecklist();

    }

    protected void requestChecklist()
    {
        getAPI().getTaskService().getChecklist(taskRepresentation.getId(),
                new Callback<ResultList<TaskRepresentation>>()
                {
                    @Override
                    public void onResponse(Call<ResultList<TaskRepresentation>> call,
                            Response<ResultList<TaskRepresentation>> response)
                    {
                        if (!response.isSuccessful())
                        {
                            onFailure(call, new Exception(response.message()));
                            return;
                        }
                        checkListTasks = response.body().getList();
                        hasCheckList = true;
                        displayCards();
                    }

                    @Override
                    public void onFailure(Call<ResultList<TaskRepresentation>> call, Throwable error)
                    {
                        hasCheckList = true;
                    }
                });
    }

    // ///////////////////////////////////////////////////////////////////////////
    // CARDS LIFECYCLE
    // ///////////////////////////////////////////////////////////////////////////
    protected void displayLoading()
    {
        hide(R.id.details_container);
        show(R.id.details_loading);
        show(R.id.progressbar);
        hide(R.id.empty);
    }

    protected void displayData()
    {
        show(R.id.details_container);
        hide(R.id.details_loading);
        hide(R.id.task_details_help_card);
        hide(R.id.progressbar);
        hide(R.id.empty);
    }

    protected void displayHelp()
    {
        show(R.id.details_container);
        hide(R.id.details_loading);
        hide(R.id.progressbar);
        hide(R.id.empty);
        hide(R.id.task_details_people_card);
        hide(R.id.details_contents_card);
        hide(R.id.task_details_checklist_card);
        if (isEnded)
        {
            hide(R.id.task_details_help_card);
        }
        else
        {
            createHelpSection();
        }
    }

    protected void displayError()
    {
        hide(R.id.details_container);
        show(R.id.details_loading);
        hide(R.id.progressbar);
        show(R.id.empty);
    }

    protected void displayCards()
    {
        if (hasPeopleLoaded && hasContentLoaded && hasCheckList)
        {
            if (people.isEmpty() && relatedContentRepresentations.isEmpty() && checkListTasks.isEmpty())
            {
                displayHelp();
            }
            else
            {
                displayCheckList(checkListTasks);
                displayPeopleSection(people);
                displayContents(relatedContentRepresentations);
                displayData();
            }
        }
        else
        {
            displayLoading();
        }
    }

    protected void displayInfo()
    {
        if (taskRepresentation == null) { return; }

        // What's the status ?
        isEnded = (taskRepresentation.getEndDate() != null);

        // Header
        ((TextView) viewById(R.id.task_details_name)).setText(taskRepresentation.getName());

        // Description
        descriptionHolder = new TwoLinesViewHolder(viewById(R.id.task_details_description));
        description = taskRepresentation.getDescription();
        displayDescription(description);

        // DUE DATE
        dueDateHolder = new TwoLinesViewHolder(viewById(R.id.task_details_due));
        dueAt = taskRepresentation.getDueDate();
        displayDueDate(dueAt);

        // ASSIGNEE
        assigneeHolder = new TwoLinesViewHolder(viewById(R.id.task_details_assignee));
        assignee = taskRepresentation.getAssignee();
        displayAssignee(assignee != null ? assignee.getFullname() : null);

        // FORM
        formHolder = new TwoLinesViewHolder(viewById(R.id.task_details_form));
        formKey = taskRepresentation.getFormKey();

        // Display Action associated
        displayOutcome();
        displayActionsSection();

    }

    private void displayOutcome()
    {
        if (isEnded)
        {
            displayCompletedProperties(taskRepresentation);
            if (formKey != null)
            {
                Button myTasks = (Button) viewById(R.id.task_action_complete);
                myTasks.setText(R.string.task_action_complete_task_with_form);
                myTasks.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v)
                    {
                        TaskFormFragment.with(getActivity()).task(taskRepresentation).back(true)
                                .display(FragmentDisplayer.PANEL_CENTRAL);
                    }
                });
            }
            else
            {
                hide(R.id.task_action_outcome_container);
            }
        }
        else
        {
            Button taskAction = (Button) viewById(R.id.task_action_complete);
            taskAction.setEnabled(true);
            if (TaskHelper.canClaim(taskRepresentation, assignee))
            {
                taskAction.setText(R.string.task_action_claim);
                taskAction.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v)
                    {
                        claim();
                        v.setEnabled(false);
                    }
                });
            }
            else if (formKey != null)
            {
                taskAction.setText(R.string.task_action_complete_task_with_form);
                taskAction.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v)
                    {
                        TaskFormFragment.with(getActivity()).task(taskRepresentation)
                                .bindFragmentTag(BundleUtils.getString(getArguments(), ARGUMENT_BIND_FRAGMENT_TAG))
                                .back(true).display();
                        v.setEnabled(false);
                    }
                });
            }
            else
            {
                taskAction.setText(R.string.task_action_complete);
                taskAction.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v)
                    {
                        completeTask();
                        v.setEnabled(false);
                    }
                });
            }
        }
    }

    private void displayAssignee(String assignee)
    {
        HolderUtils.configure(assigneeHolder, getString(R.string.task_field_assignee),
                (assignee != null) ? assignee : getString(R.string.task_message_no_assignee),
                R.drawable.ic_assignment_ind_grey);
        if (TaskHelper.canReassign(taskRepresentation, getAccount().getUserId()))
        {
            View v = viewById(R.id.task_details_assignee_container);
            v.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    // Special case activiti.alfresco.com & tenantid == null
                    // User must pick user via email only
                    if (ActivitiSession.getInstance().isActivitiOnTheCloud() && getAccount().getTenantId() == null)
                    {
                        ActivitiUserPickerFragment.with(getActivity()).fragmentTag(getTag()).fieldId("assign")
                                .displayAsDialog();
                    }
                    else
                    {
                        UserPickerFragment.with(getActivity()).fragmentTag(getTag()).fieldId("assign")
                                .singleChoice(true).mode(ListingModeFragment.MODE_PICK).display();
                    }
                }
            });
        }
        else
        {
            UIUtils.setBackground(viewById(R.id.task_details_assignee_container), null);
        }
    }

    private void displayDueDate(Date due)
    {
        dueDateHolder.topText.setText(R.string.task_field_due);
        dueDateHolder.icon.setImageResource(R.drawable.ic_event_grey);

        dueAt = due;
        if (due != null)
        {
            StringBuilder builder = new StringBuilder();
            GregorianCalendar calendar = new GregorianCalendar();
            calendar.setTime(due);
            if (calendar.before(new GregorianCalendar()))
            {
                builder.append("<font color='#F44336'>");
                builder.append(DateFormat.getLongDateFormat(getActivity()).format(due.getTime()));
                builder.append("</font>");
            }
            else
            {
                builder.append(DateFormat.getLongDateFormat(getActivity()).format(due.getTime()));
            }
            dueDateHolder.bottomText.setText(builder.toString());
            dueDateHolder.bottomText.setText(Html.fromHtml(builder.toString()), TextView.BufferType.SPANNABLE);
        }
        else
        {
            dueDateHolder.bottomText.setText(R.string.task_message_no_duedate);
        }
        if (!isEnded)
        {
            viewById(R.id.task_details_due_container).setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    DatePickerFragment.newInstance(DUE_DATE, getTag()).show(getFragmentManager(),
                            DatePickerFragment.TAG);
                }
            });
        }
        else
        {
            UIUtils.setBackground(viewById(R.id.task_details_due_container), null);
        }
    }

    private void displayDescription(final String description)
    {
        descriptionHolder.bottomText.setVisibility(View.GONE);
        descriptionHolder.icon.setImageResource(R.drawable.ic_info_outline_grey);
        if (TextUtils.isEmpty(description))
        {
            descriptionHolder.topText.setText(R.string.task_message_no_description);
        }
        else
        {
            descriptionHolder.topText.setText(description);
        }
        descriptionHolder.topText.setMaxLines(15);
        descriptionHolder.topText.setSingleLine(false);
        if (!isEnded)
        {
            View v = viewById(R.id.task_details_description_container);
            v.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    EditTextDialogFragment.with(getActivity()).fieldId(EDIT_DESCRIPTION).tag(getTag())
                            .value(description).displayAsDialog();
                }
            });
        }
        else
        {
            clearBackground(viewById(R.id.task_details_description_container));
        }
    }

    private void retrieveForm()
    {
        // Unsupported below 1.3.0
        if (getVersionNumber() < ActivitiVersionNumber.VERSION_1_3_0)
        {
            hide(R.id.task_details_form_container);
            return;
        }

        // Can't change if task in process
        if (taskRepresentation.getProcessDefinitionId() != null)
        {
            hide(R.id.task_details_form_container);
            return;
        }

        // Retrieve FormModel Info
        displayFormField();
        if (formKey != null)
        {
            getAPI().getTaskService().getTaskForm(taskRepresentation.getId(),
                    new Callback<FormDefinitionRepresentation>()
                    {
                        @Override
                        public void onResponse(Call<FormDefinitionRepresentation> call,
                                Response<FormDefinitionRepresentation> response)
                        {
                            if (!response.isSuccessful())
                            {
                                onFailure(call, new Exception(response.message()));
                                return;
                            }
                            formModelName = response.body().getName();
                            formDefinitionModel = null;
                            formKey = Long.toString(response.body().getId());
                            displayFormField();
                            if (!isEnded)
                            {
                                displayOutcome();
                            }
                        }

                        @Override
                        public void onFailure(Call<FormDefinitionRepresentation> call, Throwable error)
                        {

                        }
                    });
        }
    }

    private void displayFormField()
    {
        if (isEnded)
        {
            hide(R.id.task_details_form_container);
            return;
        }

        show(R.id.task_details_form_container);
        HolderUtils.configure(formHolder, getString(R.string.task_field_form),
                getString(R.string.task_action_retrieve_info), R.drawable.ic_assignment_grey);

        View v = viewById(R.id.task_details_form_container);
        v.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                AttachFormTaskDialogFragment.with(getActivity()).bindFragmentTag(getTag())
                        .taskId(taskRepresentation.getId()).formKey(formKey != null ? Long.parseLong(formKey) : null)
                        .displayAsDialog();
            }
        });

        ((TextView) viewById(R.id.task_details_form_container).findViewById(R.id.bottomtext))
                .setText(formModelName != null ? formModelName
                        : formDefinitionModel != null ? formDefinitionModel.getName()
                                : getString(R.string.task_message_no_form));
    }

    private void displayCompletedProperties(TaskRepresentation taskRepresentation)
    {
        TwoLinesViewHolder vh = HolderUtils.configure((LinearLayout) viewById(R.id.task_details_property_container),
                R.layout.row_two_lines_inverse, getString(R.string.task_field_ended),
                Formatter.formatToRelativeDate(getActivity(), taskRepresentation.getEndDate()),
                R.drawable.ic_history_grey);

        if (viewById(R.id.task_container_large) != null)
        {
            ((RelativeLayout) vh.icon.getParent()).setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        vh = HolderUtils.configure((LinearLayout) viewById(R.id.task_details_property_container),
                R.layout.row_two_lines_inverse, getString(R.string.task_field_duration),
                Formatter.formatDuration(getActivity(), taskRepresentation.getDuration()), R.drawable.ic_schedule_grey);

        if (viewById(R.id.task_container_large) != null)
        {
            ((RelativeLayout) vh.icon.getParent()).setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    private void displayProcessProperty(ProcessInstanceRepresentation processInstanceRepresentation)
    {
        if (processInstanceRepresentation == null
                || viewById(R.id.task_details_part_of_container).findViewById(R.id.bottomtext) == null)
        {
            HolderUtils.configure((LinearLayout) viewById(R.id.task_details_part_of_container),
                    R.layout.row_two_lines_inverse_borderless, getString(R.string.task_field_process_instance),
                    getString(R.string.task_action_retrieve_info), R.drawable.ic_transform_grey, onProcessListener);
        }

        if (processInstanceRepresentation != null)
        {
            ((TextView) viewById(R.id.task_details_part_of_container).findViewById(R.id.bottomtext))
                    .setText(processInstanceRepresentation.getName());
        }
    }

    private void displayParentTaskProperty()
    {
        if (taskRepresentation.getParentTaskId() != null)
        {
            HolderUtils.configure((LinearLayout) viewById(R.id.task_details_part_of_container),
                    R.layout.row_two_lines_inverse_borderless, getString(R.string.task_field_parent_task),
                    taskRepresentation.getParentTaskName(), R.drawable.ic_transform_grey, onParentTaskListener);
        }
    }

    private void displayActionsSection()
    {
        if (getVersionNumber() < ActivitiVersionNumber.VERSION_1_3_0)
        {
            hide(R.id.task_action_add_task_cheklist);
        }
        else
        {
            viewById(R.id.task_action_add_task_cheklist).setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    CreateStandaloneTaskDialogFragment.with(getActivity()).taskId(taskRepresentation.getId())
                            .displayAsDialog();
                    // TaskChecklistFragment.with(getActivity()).taskId(taskRepresentation.getId()).display();
                }
            });
        }

        if (isEnded)
        {
            hide(R.id.task_actions_container_bar);
            hide(R.id.task_actions_container);
        }
        else
        {
            viewById(R.id.task_action_involve).setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    startInvolveAction();
                }
            });

            viewById(R.id.task_action_add_content).setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    ContentTransferManager.requestGetContent(TaskDetailsFoundationFragment.this);
                }
            });

            viewById(R.id.task_action_add_comment).setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    ((MainActivity) getActivity())
                            .setRightMenuVisibility(!((MainActivity) getActivity()).isRightMenuVisible());
                }
            });
        }
    }

    private void displayPeopleSection(List<LightUserRepresentation> people)
    {
        show(R.id.task_details_people_card);
        if (people.isEmpty() || isEnded)
        {
            hide(R.id.task_details_people_card);
            return;
        }

        // USER INVOLVED
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout userContainer = (LinearLayout) viewById(R.id.task_details_people_container);
        userContainer.removeAllViews();
        View v;
        if (people == null || people.isEmpty())
        {
            v = inflater.inflate(R.layout.row_single_line, userContainer, false);
            ((TextView) v.findViewById(R.id.toptext)).setText(R.string.task_message_no_people_involved);
            v.findViewById(R.id.icon).setVisibility(View.GONE);
            userContainer.addView(v);
        }
        else
        {
            TwoLinesViewHolder vh;
            for (LightUserRepresentation user : people)
            {
                v = inflater.inflate(R.layout.row_two_lines_swipe, userContainer, false);
                v.setTag(user.getId());
                String fullName = user.getFullname();
                vh = HolderUtils.configure(v, fullName != null && !fullName.isEmpty() ? fullName : user.getEmail(),
                        null, R.drawable.ic_account_circle_grey);
                if (picasso != null)
                {
                    picasso.cancelRequest(vh.icon);
                    picasso.load(getAPI().getUserGroupService().getPicture(user.getId()))
                            .placeholder(R.drawable.ic_account_circle_grey).fit().transform(roundedTransformation)
                            .into(vh.icon);
                }
                SwipeLayout swipeLayout = (SwipeLayout) v.findViewById(R.id.swipe_layout);
                swipeLayout.setShowMode(SwipeLayout.ShowMode.LayDown);
                swipeLayout.setDragEdge(SwipeLayout.DragEdge.Right);

                LinearLayout actions = (LinearLayout) swipeLayout.findViewById(R.id.bottom_wrapper);
                ImageButton action = (ImageButton) inflater.inflate(R.layout.form_swipe_action_,
                        (LinearLayout) swipeLayout.findViewById(R.id.bottom_wrapper), false);
                action.setImageResource(R.drawable.ic_remove_circle_outline_white);
                action.setTag(user);
                action.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v)
                    {
                        removeInvolved(((LightUserRepresentation) v.getTag()),
                                new InvolveTaskRepresentation(((LightUserRepresentation) v.getTag()).getId()));
                    }
                });
                actions.addView(action);

                userContainer.addView(v);
            }
        }

        if (!isEnded)
        {
            v = inflater.inflate(R.layout.footer_two_buttons_borderless, userContainer, false);
            Button b = (Button) v.findViewById(R.id.button_action_left);
            b.setVisibility(View.GONE);
            b = (Button) v.findViewById(R.id.button_action_right);
            b.setText(R.string.task_action_involve);
            b.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    startInvolveAction();
                }
            });
            userContainer.addView(v);
        }
    }

    // ///////////////////////////////////////////////////////////////////////////
    // TASKS
    // ///////////////////////////////////////////////////////////////////////////
    protected void displayCheckList(List<TaskRepresentation> taskRepresentations)
    {
        if (getVersionNumber() < ActivitiVersionNumber.VERSION_1_3_0)
        {
            hide(R.id.task_details_checklist_card);
            return;
        }

        show(R.id.task_details_checklist_card);
        if (taskRepresentations == null || taskRepresentations.isEmpty() || isEnded)
        {
            hide(R.id.task_details_checklist_card);
            return;
        }

        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // TASKS
        LinearLayout activeTaskContainer = (LinearLayout) viewById(R.id.task_details_checklist_container);
        activeTaskContainer.removeAllViews();
        View v;
        if (taskRepresentations == null || taskRepresentations.isEmpty())
        {
            v = inflater.inflate(R.layout.row_single_line, activeTaskContainer, false);
            ((TextView) v.findViewById(R.id.toptext)).setText(R.string.task_help_add_first_checklist);
            HolderUtils.makeMultiLine(((TextView) v.findViewById(R.id.toptext)), 4);
            v.findViewById(R.id.icon).setVisibility(View.GONE);
            activeTaskContainer.addView(v);
        }
        else
        {
            TaskRepresentation taskCheckList;
            TwoLinesViewHolder vh;
            int max = (taskRepresentations.size() > TASKS_MAX_ITEMS) ? TASKS_MAX_ITEMS : taskRepresentations.size();
            for (int i = 0; i < max; i++)
            {
                taskCheckList = taskRepresentations.get(i);
                v = inflater.inflate(R.layout.row_three_lines_caption_borderless, activeTaskContainer, false);
                v.setTag(taskCheckList);

                vh = HolderUtils.configure(v, taskCheckList.getName(), null,
                        TaskAdapter.createAssigneeInfo(getActivity(), taskCheckList),
                        TaskAdapter.createRelativeDateInfo(getActivity(), taskCheckList),
                        R.drawable.ic_account_circle_grey);

                if (taskCheckList.getEndDate() != null)
                {
                    vh.choose.setImageResource(R.drawable.ic_done_grey);
                }
                else
                {
                    vh.choose.setImageResource(android.R.color.transparent);
                }
                vh.choose.setVisibility(View.VISIBLE);

                activeTaskContainer.addView(v);

                v.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v)
                    {
                        TaskDetailsFragment.with(getActivity()).task((TaskRepresentation) v.getTag())
                                .bindFragmentTag(getTag()).back(true).display();
                    }
                });
            }
        }
    }

    private void createHelpSection()
    {
        if (isEnded) { return; }

        show(R.id.task_details_help_card);

        // DETAILS
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout helpContainer = (LinearLayout) viewById(R.id.task_details_help_card);
        helpContainer.removeAllViews();
        View v = inflater.inflate(R.layout.card_task_help, helpContainer, false);

        // INVOLVE
        TwoLinesViewHolder vh = HolderUtils.configure(v.findViewById(R.id.help_details_involve),
                getString(R.string.task_help_add_people), null, R.drawable.ic_account_box_grey);
        HolderUtils.makeMultiLine(vh.topText, 3);
        v.findViewById(R.id.help_details_involve_container).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // Special case activiti.alfresco.com & tenantid == null
                // User must pick user via email only
                startInvolveAction();

            }
        });

        // ADD CONTENT
        vh = HolderUtils.configure(v.findViewById(R.id.help_details_add_content),
                getString(R.string.task_help_add_content), null, R.drawable.ic_insert_drive_file_grey);
        HolderUtils.makeMultiLine(vh.topText, 3);
        v.findViewById(R.id.help_details_add_content_container).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                ContentTransferManager.requestGetContent(TaskDetailsFoundationFragment.this);
            }
        });

        // COMMENT
        vh = HolderUtils.configure(v.findViewById(R.id.help_details_comment), getString(R.string.task_help_add_comment),
                null, R.drawable.ic_insert_comment_grey);
        HolderUtils.makeMultiLine(vh.topText, 3);
        v.findViewById(R.id.help_details_comment_container).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                ((MainActivity) getActivity())
                        .setRightMenuVisibility(!((MainActivity) getActivity()).isRightMenuVisible());
            }
        });

        // CHECKLIST
        vh = HolderUtils.configure(v.findViewById(R.id.help_details_add_task_checklist),
                getString(R.string.task_help_add_checklist), null, R.drawable.ic_add_circle_grey);
        HolderUtils.makeMultiLine(vh.topText, 3);
        v.findViewById(R.id.help_details_add_task_checklist_container).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                CreateStandaloneTaskDialogFragment.with(getActivity()).taskId(taskRepresentation.getId())
                        .displayAsDialog();
            }
        });

        helpContainer.addView(v);
    }

    // ///////////////////////////////////////////////////////////////////////////
    // ACTIONS
    // ///////////////////////////////////////////////////////////////////////////
    private void startInvolveAction()
    {
        if (ActivitiSession.getInstance().isActivitiOnTheCloud() && getAccount().getTenantId() == null)
        {
            ActivitiUserPickerFragment.with(getActivity()).fragmentTag(getTag()).fieldId("involve").displayAsDialog();
        }
        else
        {
            UserPickerFragment.with(getActivity()).fragmentTag(getTag()).fieldId("involve")
                    .taskId(taskRepresentation.getId()).singleChoice(true).mode(ListingModeFragment.MODE_PICK)
                    .display();
        }
    }

    private void completeTask()
    {
        getAPI().getTaskService().complete(taskId, new Callback<Void>()
        {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response)
            {
                // Analytics
                AnalyticsHelper.reportOperationEvent(getActivity(), AnalyticsManager.CATEGORY_TASK,
                        AnalyticsManager.ACTION_COMPLETE_TASK, AnalyticsManager.LABEL_WITHOUT_FORM, 1,
                        !response.isSuccessful());

                if (!response.isSuccessful())
                {
                    onFailure(call, new Exception(response.message()));
                    return;
                }
                try
                {
                    EventBusManager.getInstance()
                            .post(new CompleteTaskEvent(null, taskId, taskRepresentation.getCategory()));
                }
                catch (Exception e)
                {
                    // Do nothing
                }

                Snackbar.make(getActivity().findViewById(R.id.left_panel), R.string.task_alert_completed,
                        Snackbar.LENGTH_SHORT).show();

                Fragment fr = getAttachedFragment();
                if (fr != null && fr instanceof TasksFragment)
                {
                    if (DisplayUtils.hasCentralPane(getActivity()))
                    {
                        ((TasksFragment) fr).refreshOutside();
                    }
                    else
                    {
                        ((TasksFragment) fr).refresh();
                    }
                }

                getActivity().getSupportFragmentManager().popBackStackImmediate();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable error)
            {
                Snackbar.make(getActivity().findViewById(R.id.left_panel), error.getMessage(), Snackbar.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void claim()
    {
        getAPI().getTaskService().claimTask(taskId, new Callback<Void>()
        {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response)
            {
                // Analytics
                AnalyticsHelper.reportOperationEvent(getActivity(), AnalyticsManager.CATEGORY_TASK,
                        AnalyticsManager.ACTION_CLAIM, AnalyticsManager.LABEL_TASK, 1, !response.isSuccessful());

                if (!response.isSuccessful())
                {
                    onFailure(call, new Exception(response.message()));
                    return;
                }
                assignee = ActivitiAccountManager.getInstance(getActivity()).getUser();
                displayAssignee(assignee != null ? assignee.getFullname() : null);
                displayOutcome();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable error)
            {
                Snackbar.make(getActivity().findViewById(R.id.left_panel), error.getMessage(), Snackbar.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void assign(LightUserRepresentation user)
    {
        AssignTaskRepresentation body = (ActivitiSession.getInstance().isActivitiOnTheCloud())
                ? new AssignTaskRepresentation(user.getEmail()) : new AssignTaskRepresentation(user.getId());

        getAPI().getTaskService().assign(taskId, body, new Callback<TaskRepresentation>()
        {
            @Override
            public void onResponse(Call<TaskRepresentation> call, Response<TaskRepresentation> response)
            {
                // Analytics
                AnalyticsHelper.reportOperationEvent(getActivity(), AnalyticsManager.CATEGORY_TASK,
                        AnalyticsManager.ACTION_REASSIGN, AnalyticsManager.LABEL_USER, 1, !response.isSuccessful());

                if (!response.isSuccessful())
                {
                    onFailure(call, new Exception(response.message()));
                    return;
                }
                displayAssignee(
                        response.body().getAssignee() != null ? response.body().getAssignee().getFullname() : null);
                Snackbar.make(getActivity().findViewById(R.id.left_panel),
                        String.format(getString(R.string.task_alert_assigned),
                                task.name, response.body().getAssignee() != null
                                        ? response.body().getAssignee().getFullname() : ""),
                        Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<TaskRepresentation> call, Throwable error)
            {
                Snackbar.make(getActivity().findViewById(R.id.left_panel), error.getMessage(), Snackbar.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void assign(final String userEmail)
    {
        AssignTaskRepresentation body = new AssignTaskRepresentation(userEmail);
        getAPI().getTaskService().assign(taskId, body, new Callback<TaskRepresentation>()
        {
            @Override
            public void onResponse(Call<TaskRepresentation> call, Response<TaskRepresentation> response)
            {
                // Analytics
                AnalyticsHelper.reportOperationEvent(getActivity(), AnalyticsManager.CATEGORY_TASK,
                        AnalyticsManager.ACTION_REASSIGN, AnalyticsManager.LABEL_USER, 1, !response.isSuccessful());

                if (!response.isSuccessful())
                {
                    onFailure(call, new Exception(response.message()));
                    return;
                }
                displayAssignee(
                        response.body().getAssignee() != null ? response.body().getAssignee().getFullname() : null);

                Snackbar.make(getActivity().findViewById(R.id.left_panel),
                        String.format(getString(R.string.task_alert_assigned), userEmail, taskRepresentation.getName()),
                        Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<TaskRepresentation> call, Throwable error)
            {
                Snackbar.make(getActivity().findViewById(R.id.left_panel), error.getMessage(), Snackbar.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void involve(final LightUserRepresentation user)
    {
        getAPI().getTaskService().involve(taskId, new InvolveTaskRepresentation(user.getId()), new Callback<Void>()
        {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response)
            {

                // Analytics
                AnalyticsHelper.reportOperationEvent(getActivity(), AnalyticsManager.CATEGORY_TASK,
                        AnalyticsManager.ACTION_USER_INVOLVED, AnalyticsManager.ACTION_ADD, 1,
                        !response.isSuccessful());

                if (!response.isSuccessful())
                {
                    onFailure(call, new Exception(response.message()));
                    return;
                }
                displayPeopleSection(people);
                Snackbar.make(getActivity().findViewById(R.id.left_panel),
                        String.format(getString(R.string.task_alert_person_involved), user.getFullname(),
                                taskRepresentation.getName()),
                        Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable error)
            {
                Snackbar.make(getActivity().findViewById(R.id.left_panel), error.getMessage(), Snackbar.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void involve(String userEmail)
    {
        getAPI().getTaskService().involve(taskId, new InvolveTaskRepresentation(userEmail), new Callback<Void>()
        {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response)
            {
                // Analytics
                AnalyticsHelper.reportOperationEvent(getActivity(), AnalyticsManager.CATEGORY_TASK,
                        AnalyticsManager.ACTION_USER_INVOLVED, AnalyticsManager.ACTION_ADD, 1,
                        !response.isSuccessful());

                if (!response.isSuccessful())
                {
                    onFailure(call, new Exception(response.message()));
                    return;
                }
                refreshInfo();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable error)
            {
                Snackbar.make(getActivity().findViewById(R.id.left_panel), error.getMessage(), Snackbar.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void edit(final UpdateTaskRepresentation update)
    {
        getAPI().getTaskService().edit(taskId, update, new Callback<TaskRepresentation>()
        {
            @Override
            public void onResponse(Call<TaskRepresentation> call, Response<TaskRepresentation> response)
            {
                // Analytics
                AnalyticsHelper.reportOperationEvent(getActivity(), AnalyticsManager.CATEGORY_TASK,
                        AnalyticsManager.ACTION_EDIT, AnalyticsManager.LABEL_TASK, 1, !response.isSuccessful());

                if (!response.isSuccessful())
                {
                    onFailure(call, new Exception(response.message()));
                    return;
                }
                taskRepresentation = response.body();
                displayDescription(description);
                displayDueDate(dueAt);
            }

            @Override
            public void onFailure(Call<TaskRepresentation> call, Throwable error)
            {
                Snackbar.make(getActivity().findViewById(R.id.left_panel), error.getMessage(), Snackbar.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void removeInvolved(final LightUserRepresentation userRemoved, final InvolveTaskRepresentation involvement)
    {
        getAPI().getTaskService().removeInvolved(taskId, involvement, new Callback<Void>()
        {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response)
            {
                // Analytics
                AnalyticsHelper.reportOperationEvent(getActivity(), AnalyticsManager.CATEGORY_TASK,
                        AnalyticsManager.ACTION_USER_INVOLVED, AnalyticsManager.ACTION_REMOVE, 1,
                        !response.isSuccessful());

                if (!response.isSuccessful())
                {
                    onFailure(call, new Exception(response.message()));
                    return;
                }
                people.remove(userRemoved);
                if (people.isEmpty())
                {
                    displayCards();
                }
                else
                {
                    displayPeopleSection(people);
                }

                Snackbar.make(getActivity().findViewById(R.id.left_panel),
                        String.format(getString(R.string.task_alert_person_no_longer_involved),
                                userRemoved.getFullname(), taskRepresentation.getName()),
                        Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable error)
            {
                // SnackbarManager.show(Snackbar.with(getActivity()).text(error.getMessage()));
            }
        });
    }

    public void removeForm()
    {
        getAPI().getTaskService().removeForm(taskId, new Callback<Void>()
        {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response)
            {
                // Analytics
                AnalyticsHelper.reportOperationEvent(getActivity(), AnalyticsManager.CATEGORY_TASK,
                        AnalyticsManager.ACTION_FORM, AnalyticsManager.ACTION_REMOVE, 1, !response.isSuccessful());

                if (!response.isSuccessful())
                {
                    onFailure(call, new Exception(response.message()));
                    return;
                }
                Snackbar.make(getActivity().findViewById(R.id.left_panel),
                        String.format(getString(R.string.task_alert_form_removed),
                                (formDefinitionModel != null) ? formDefinitionModel.getName() : formModelName),
                        Snackbar.LENGTH_SHORT).show();
                formKey = null;
                formDefinitionModel = null;
                formModelName = null;
                displayFormField();
                displayOutcome();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable error)
            {
                Snackbar.make(getActivity().findViewById(R.id.left_panel), error.getMessage(), Snackbar.LENGTH_SHORT)
                        .show();
            }
        });
    }

    public void attachForm(final ModelRepresentation formModel)
    {
        AttachFormTaskRepresentation rep = new AttachFormTaskRepresentation(formModel.getId());
        getAPI().getTaskService().attachForm(taskId, rep, new Callback<Void>()
        {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response)
            {
                // Analytics
                AnalyticsHelper.reportOperationEvent(getActivity(), AnalyticsManager.CATEGORY_TASK,
                        AnalyticsManager.ACTION_FORM, AnalyticsManager.ACTION_ADD, 1, !response.isSuccessful());

                if (!response.isSuccessful())
                {
                    onFailure(call, new Exception(response.message()));
                    return;
                }
                formDefinitionModel = formModel;
                formModelName = null;
                // awaits retrieve form to get real formkey
                formKey = "-1";
                retrieveForm();
                displayFormField();
                Snackbar.make(getActivity().findViewById(R.id.left_panel),
                        String.format(getString(R.string.task_alert_form_attached), formModel.getName()),
                        Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable error)
            {
                Snackbar.make(getActivity().findViewById(R.id.left_panel), error.getMessage(), Snackbar.LENGTH_SHORT)
                        .show();
            }
        });
    }

    // //////////////////////////////////////////////////////////////////////////////////////
    // PICKER CALLBACK
    // //////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onDatePicked(String dateId, GregorianCalendar gregorianCalendar)
    {
        dueAt = gregorianCalendar.getTime();
        edit(new UpdateTaskRepresentation(taskRepresentation.getName(), taskRepresentation.getDescription(), dueAt));
    }

    @Override
    public void onDateClear(String dateId)
    {
        dueAt = null;
        edit(new UpdateTaskRepresentation(taskRepresentation.getName(), taskRepresentation.getDescription(), dueAt));
    }

    @Override
    public void onPersonSelected(String fieldId, Map<String, LightUserRepresentation> p)
    {
        if ("involve".equals(fieldId))
        {
            involve(p.get(p.keySet().toArray()[0]));
        }
        else if ("assign".equals(fieldId))
        {
            assign(p.get(p.keySet().toArray()[0]));
        }
    }

    @Override
    public void onUserEmailSelected(String fieldId, String username)
    {
        if ("involve".equals(fieldId))
        {
            involve(username);
        }
        else if ("assign".equals(fieldId))
        {
            assign(username);
        }
    }

    @Override
    public void onPersonClear(String fieldId)
    {
        Snackbar.make(getActivity().findViewById(R.id.left_panel), R.string.cleared, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public Map<String, LightUserRepresentation> getPersonSelected(String fieldId)
    {
        return new HashMap<>(0);
    }

    @Override
    public void onTextEdited(int id, String newValue)
    {
        description = newValue;
        edit(new UpdateTaskRepresentation(taskRepresentation.getName(), newValue, dueAt));
    }

    @Override
    public void onTextClear(int valueId)
    {
        description = null;
        edit(new UpdateTaskRepresentation(taskRepresentation.getName(), null, dueAt));
    }

}
