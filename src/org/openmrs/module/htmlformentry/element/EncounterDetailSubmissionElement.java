package org.openmrs.module.htmlformentry.element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Person;
import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.FormSubmissionError;
import org.openmrs.module.htmlformentry.HtmlFormEntryUtil;
import org.openmrs.module.htmlformentry.FormEntryContext.Mode;
import org.openmrs.module.htmlformentry.action.FormSubmissionControllerAction;
import org.openmrs.module.htmlformentry.widget.DateWidget;
import org.openmrs.module.htmlformentry.widget.ErrorWidget;
import org.openmrs.module.htmlformentry.widget.LocationWidget;
import org.openmrs.module.htmlformentry.widget.PersonByNameComparator;
import org.openmrs.module.htmlformentry.widget.PersonWidget;
import org.openmrs.module.htmlformentry.widget.TimeWidget;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.util.StringUtils;

/**
 * Holds the widgets used to represent an Encounter details, and serves as both the HtmlGeneratorElement 
 * and the FormSubmissionControllerAction for Encounter details.
 */
public class EncounterDetailSubmissionElement implements HtmlGeneratorElement, FormSubmissionControllerAction {

	private DateWidget dateWidget;
	private ErrorWidget dateErrorWidget;
	private TimeWidget timeWidget;
	private ErrorWidget timeErrorWidget;
	private PersonWidget providerWidget;
	private ErrorWidget providerErrorWidget;
	private LocationWidget locationWidget;
	private ErrorWidget locationErrorWidget;

	/**
	 * Construct a new EncounterDetailSubmissionElement
	 * @param context
	 * @param parameters
	 */
	public EncounterDetailSubmissionElement(FormEntryContext context, Map<String, Object> parameters) {
		
		// Register Date and Time widgets, if appropriate
		if (Boolean.TRUE.equals(parameters.get("date"))) {
			
			dateWidget = new DateWidget();
			dateErrorWidget = new ErrorWidget();
			
			if (context.getExistingEncounter() != null) {
				dateWidget.setInitialValue(context.getExistingEncounter().getEncounterDatetime());
			} 
			else if (parameters.get("defaultDate") != null) {
				dateWidget.setInitialValue(parameters.get("defaultDate"));
			}
			
			if ("true".equals(parameters.get("showTime"))) {
				timeWidget = new TimeWidget();
				timeErrorWidget = new ErrorWidget();
				if (context.getExistingEncounter() != null) {
					timeWidget.setInitialValue(context.getExistingEncounter().getEncounterDatetime());
				} 
				else if (parameters.get("defaultDate") != null) {
					timeWidget.setInitialValue(parameters.get("defaultDate"));
				}
				context.registerWidget(timeWidget);
				context.registerErrorWidget(timeWidget, timeErrorWidget);
			}
			context.registerWidget(dateWidget);
			context.registerErrorWidget(dateWidget, dateErrorWidget);
		}
		
		// Register Provider widgets, if appropriate
		if (Boolean.TRUE.equals(parameters.get("provider"))) {
			
			providerWidget = new PersonWidget();
			providerErrorWidget = new ErrorWidget();
			
			List<Person> options = new ArrayList<Person>();
			boolean sortOptions = false;
			
			// If specific persons are specified, display only those persons in order
			String personsParam = (String)parameters.get("persons");
			if (personsParam != null) {
				for (String s : personsParam.split(",")) {
					Person p = HtmlFormEntryUtil.getPerson(s);
					if (p == null) {
						throw new RuntimeException("Cannot find Person: " + s);
					}
					options.add(p);
				}
			}
			
			// Only if specific person ids are not passed in do we get by user Role
			if (options.isEmpty()) {
				
				List<User> users = new ArrayList<User>();
				
				// If the "role" attribute is passed in, limit to users with this role
				if (parameters.get("role") != null) {
					Role role = Context.getUserService().getRole((String) parameters.get("role"));
					if (role == null) {
						throw new RuntimeException("Cannot find role: " + parameters.get("role"));
					}
					else {
						users = Context.getUserService().getUsersByRole(role);
					}
				}
				
				// Otherwise, limit to users with the default OpenMRS PROVIDER role, 
				else {
					String defaultRole = OpenmrsConstants.PROVIDER_ROLE;
					Role role = Context.getUserService().getRole(defaultRole);
					if (role != null) {
						users = Context.getUserService().getUsersByRole(role);
					}
					// If this role isn't used, default to all Users
					if (users.isEmpty()) {
						users = Context.getUserService().getAllUsers();
					}
				}
				
				for (User u : users) {
					options.add(u.getPerson());
				}
				sortOptions = true;
			}
			
			// Set default values as appropriate
			Person defaultProvider = null;
			if (context.getExistingEncounter() != null) {
				defaultProvider = context.getExistingEncounter().getProvider();
				if (!options.contains(defaultProvider)) {
					options.add(defaultProvider);
				}
			}
			else {
				String defParam = (String) parameters.get("default");
				if (StringUtils.hasText(defParam)) {
					if ("currentuser".equalsIgnoreCase(defParam)) {
						defaultProvider = Context.getAuthenticatedUser().getPerson();
					} 
					else {
						defaultProvider = HtmlFormEntryUtil.getPerson(defParam);
					}
					if (defaultProvider == null) {
						throw new IllegalArgumentException("Invalid default provider specified for encounter: " + defParam);
					}
				}
			}
			
			if (sortOptions) {
				Collections.sort(options, new PersonByNameComparator());
			}
			providerWidget.setOptions(options);
			providerWidget.setInitialValue(defaultProvider);
			
			context.registerWidget(providerWidget);
			context.registerErrorWidget(providerWidget, providerErrorWidget);
		}
		
		// Register Location widgets, if appropriate
		if (Boolean.TRUE.equals(parameters.get("location"))) {
			
			locationWidget = new LocationWidget();
			locationErrorWidget = new ErrorWidget();
			
			// If the "order" attribute is passed in, limit to the specified locations in order
			if (parameters.get("order") != null) {
				List<Location> locations = new ArrayList<Location>();
				String[] temp = ((String) parameters.get("order")).split(",");
				for (String s : temp) {
					Location loc = HtmlFormEntryUtil.getLocation(s);
					if (loc == null) {
						throw new RuntimeException("Cannot find location: " + loc);
					}
					locations.add(loc);
				}
				locationWidget.setOptions(locations);
			}
			
			// Set default values
			if (context.getExistingEncounter() != null) {
				locationWidget.setInitialValue(context.getExistingEncounter().getLocation());
			} 
			else {
				String defaultLocId = (String) parameters.get("default");
				if (StringUtils.hasText(defaultLocId)) {
					Location defaultLoc = HtmlFormEntryUtil.getLocation(defaultLocId);
					locationWidget.setInitialValue(defaultLoc);
				}
			}
			context.registerWidget(locationWidget);
			context.registerErrorWidget(locationWidget, locationErrorWidget);
		}
	}

	/**
	 * @see HtmlGeneratorElement#generateHtml(FormEntryContext)
	 */
	public String generateHtml(FormEntryContext context) {
		StringBuilder ret = new StringBuilder();
		if (dateWidget != null) {
			ret.append(dateWidget.generateHtml(context));
			if (context.getMode() != Mode.VIEW)
				ret.append(dateErrorWidget.generateHtml(context));
		}
		if (timeWidget != null) {
			ret.append("&nbsp;");
			ret.append(timeWidget.generateHtml(context));
			if (context.getMode() != Mode.VIEW)
				ret.append(timeErrorWidget.generateHtml(context));
		}
		if (providerWidget != null) {
			ret.append(providerWidget.generateHtml(context));
			if (context.getMode() != Mode.VIEW)
				ret.append(providerErrorWidget.generateHtml(context));
		}
		if (locationWidget != null) {
			ret.append(locationWidget.generateHtml(context));
			if (context.getMode() != Mode.VIEW)
				ret.append(locationErrorWidget.generateHtml(context));
		}
		return ret.toString();
	}

	/**
	 * @see FormSubmissionControllerAction#validateSubmission(FormEntryContext, HttpServletRequest)
	 */
	public Collection<FormSubmissionError> validateSubmission(FormEntryContext context, HttpServletRequest submission) {
		List<FormSubmissionError> ret = new ArrayList<FormSubmissionError>();
		
		try {
			if (dateWidget != null) {
				Date date = (Date) dateWidget.getValue(context, submission);
				if (timeWidget != null) {
					Date time = (Date) timeWidget.getValue(context, submission);
					date = HtmlFormEntryUtil.combineDateAndTime(date, time);
				}
				if (date == null)
					throw new Exception("htmlformentry.error.required");
				if (OpenmrsUtil.compare((Date) date, new Date()) > 0)
					throw new Exception("htmlformentry.error.cannotBeInFuture");
			}
		} catch (Exception ex) {
			ret.add(new FormSubmissionError(context
					.getFieldName(dateErrorWidget), Context
					.getMessageSourceService().getMessage(ex.getMessage())));
		}

		try {
			if (providerWidget != null) {
				Object provider = providerWidget.getValue(context, submission);
				if (provider == null)
					throw new Exception("required");
			}
		} catch (Exception ex) {
			ret.add(new FormSubmissionError(context
					.getFieldName(providerErrorWidget), Context
					.getMessageSourceService().getMessage(ex.getMessage())));
		}
		
		try {
			if (locationWidget != null) {
				Object location = locationWidget.getValue(context, submission);
				if (location == null)
					throw new Exception("required");
			}
		} catch (Exception ex) {
			ret.add(new FormSubmissionError(context
					.getFieldName(locationErrorWidget), Context
					.getMessageSourceService().getMessage(ex.getMessage())));
		}
		return ret;
	}

	/**
	 * @see FormSubmissionControllerAction#handleSubmission(FormEntrySession, HttpServletRequest)
	 */
	public void handleSubmission(FormEntrySession session, HttpServletRequest submission) {
		if (dateWidget != null) {
			Date date = (Date) dateWidget.getValue(session.getContext(), submission);
			session.getSubmissionActions().getCurrentEncounter().setEncounterDatetime(date);
		}
		if (timeWidget != null) {
			Date time = (Date) timeWidget.getValue(session.getContext(), submission);
			Encounter e = session.getSubmissionActions().getCurrentEncounter();
			Date dateAndTime = HtmlFormEntryUtil.combineDateAndTime(e.getEncounterDatetime(), time);
			e.setEncounterDatetime(dateAndTime);
		}
		if (providerWidget != null) {
			Person person = (Person) providerWidget.getValue(session.getContext(), submission);
			session.getSubmissionActions().getCurrentEncounter().setProvider(person);
		}
		if (locationWidget != null) {
			Location location = (Location) locationWidget.getValue(session.getContext(), submission);
			session.getSubmissionActions().getCurrentEncounter().setLocation(location);
		}
	}
}
