/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.config

import tausi.sample.model.*

/** Configuration for the survey. */
object SurveyConfig:

  /** Survey questions for page one. */
  val pageOneQuestions: List[SurveyQuestion] = List(
    SurveyQuestion(
      id = "overall_satisfaction",
      text = "How satisfied are you with our service overall?",
      questionType = QuestionType.Rating,
      required = true,
      options = None
    ),
    SurveyQuestion(
      id = "recommendation_likelihood",
      text = "How likely are you to recommend us to a colleague?",
      questionType = QuestionType.Rating,
      required = true,
      options = None
    ),
    SurveyQuestion(
      id = "service_quality",
      text = "How would you rate the quality of our products/services?",
      questionType = QuestionType.Rating,
      required = true,
      options = None
    ),
    SurveyQuestion(
      id = "communication_rating",
      text = "How would you rate our communication with you?",
      questionType = QuestionType.Rating,
      required = true,
      options = None
    )
  )

  // === Dynamic field configuration ===

  /** Industry options. */
  val industries: List[String] = List(
    "Technology",
    "Healthcare",
    "Finance",
    "Manufacturing",
    "Retail"
  )

  /** Product lines per industry. */
  val productsByIndustry: Map[String, List[String]] = Map(
    "Technology" -> List("Cloud Services", "Enterprise Software", "Security Solutions", "Data Analytics"),
    "Healthcare" -> List("Patient Management", "Clinical Analytics", "Telehealth", "Compliance Tools"),
    "Finance" -> List("Trading Platform", "Risk Management", "Payment Processing", "Regulatory Reporting"),
    "Manufacturing" -> List("Supply Chain", "Quality Control", "IoT Monitoring", "Predictive Maintenance"),
    "Retail" -> List("E-commerce Platform", "Inventory Management", "Customer Analytics", "POS Systems")
  )

  /** Feedback focus areas based on industry + product combination. */
  val feedbackTopics: Map[(String, String), List[String]] = Map(
    // Technology
    ("Technology", "Cloud Services") -> List("Uptime & Reliability", "Scalability", "Cost Optimization", "Migration Support"),
    ("Technology", "Enterprise Software") -> List("User Experience", "Integration", "Customization", "Training"),
    ("Technology", "Security Solutions") -> List("Threat Detection", "Compliance", "Incident Response", "Reporting"),
    ("Technology", "Data Analytics") -> List("Query Performance", "Visualization", "Data Connectors", "Real-time Processing"),
    // Healthcare
    ("Healthcare", "Patient Management") -> List("Workflow Efficiency", "Interoperability", "Patient Portal", "Scheduling"),
    ("Healthcare", "Clinical Analytics") -> List("Report Accuracy", "Dashboard Usability", "Data Quality", "Alert System"),
    ("Healthcare", "Telehealth") -> List("Video Quality", "Patient Experience", "Provider Interface", "Integration"),
    ("Healthcare", "Compliance Tools") -> List("Audit Trails", "Policy Management", "Training Modules", "Reporting"),
    // Finance
    ("Finance", "Trading Platform") -> List("Execution Speed", "Market Data", "Order Management", "Analytics"),
    ("Finance", "Risk Management") -> List("Model Accuracy", "Stress Testing", "Reporting", "Alert Configuration"),
    ("Finance", "Payment Processing") -> List("Transaction Speed", "Fraud Detection", "Reconciliation", "Multi-currency"),
    ("Finance", "Regulatory Reporting") -> List("Accuracy", "Timeliness", "Format Support", "Audit Trail"),
    // Manufacturing
    ("Manufacturing", "Supply Chain") -> List("Visibility", "Demand Planning", "Supplier Integration", "Cost Tracking"),
    ("Manufacturing", "Quality Control") -> List("Defect Detection", "Traceability", "Reporting", "Workflow"),
    ("Manufacturing", "IoT Monitoring") -> List("Device Management", "Alert System", "Data Collection", "Dashboard"),
    ("Manufacturing", "Predictive Maintenance") -> List("Prediction Accuracy", "Alert Timing", "Integration", "ROI Tracking"),
    // Retail
    ("Retail", "E-commerce Platform") -> List("Site Performance", "Checkout Experience", "Mobile Experience", "Search"),
    ("Retail", "Inventory Management") -> List("Accuracy", "Reorder Alerts", "Multi-location", "Reporting"),
    ("Retail", "Customer Analytics") -> List("Segmentation", "Journey Mapping", "Attribution", "Personalization"),
    ("Retail", "POS Systems") -> List("Transaction Speed", "Hardware Reliability", "Integration", "Reporting")
  )

  /** Get products for selected industry. */
  def getProducts(industry: String): List[String] =
    productsByIndustry.getOrElse(industry, List.empty)

  /** Get feedback topics for industry + product combination. */
  def getFeedbackTopics(industry: String, product: String): List[String] =
    feedbackTopics.getOrElse((industry, product), List.empty)

  /** Survey questions for page two (static questions only - dynamic ones rendered separately). */
  val pageTwoQuestions: List[SurveyQuestion] = List(
    SurveyQuestion(
      id = "best_aspect",
      text = "What do you like most about working with us?",
      questionType = QuestionType.Text,
      required = true,
      options = None
    ),
    SurveyQuestion(
      id = "improvement_suggestion",
      text = "What is one thing we could do better?",
      questionType = QuestionType.Text,
      required = false,
      options = None
    ),
    SurveyQuestion(
      id = "additional_feedback",
      text = "Any additional comments or feedback?",
      questionType = QuestionType.Text,
      required = false,
      options = None
    )
  )

  /** All survey pages. */
  val surveyPages: List[SurveyPage] = List(
    SurveyPage(
      id = "page_one",
      title = "Service Experience",
      description = "Please rate your experience with our services.",
      questions = pageOneQuestions
    ),
    SurveyPage(
      id = "page_two",
      title = "Feedback & Suggestions",
      description = "Help us improve by sharing your thoughts.",
      questions = pageTwoQuestions
    )
  )

  /** Company name for branding. */
  val companyName: String = "Acme Corporation"

  /** Survey title. */
  val surveyTitle: String = "Customer Satisfaction Survey"

  /** Survey subtitle/description. */
  val surveyDescription: String =
    "Your feedback helps us improve. This survey takes approximately 3-5 minutes to complete."
end SurveyConfig
