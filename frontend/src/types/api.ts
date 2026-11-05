// 백엔드 DTO 와 1:1 대응하는 타입 정의

export type ProviderMode = 'REAL' | 'MOCK';
export type ComponentType = 'ATTENDANCE' | 'ASSIGNMENT' | 'MIDTERM' | 'FINAL' | 'QUIZ' | 'OTHER';
export type AttendanceStatus = 'ATTENDED' | 'LATE' | 'ABSENT' | 'EXCUSED';
export type AssignmentStatus = 'PENDING' | 'SUBMITTED' | 'MISSED';
export type RiskStatus = 'SAFE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'F_CONFIRMED' | 'CANNOT_CALCULATE';
export type LatenessLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface Schedule {
  id?: number | null;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  room?: string | null;
}

export interface GradeComponent {
  id?: number | null;
  type: ComponentType;
  name: string;
  weight: number;
  score?: number | null;
}

export interface CourseRequest {
  name: string;
  professor?: string | null;
  credits?: number | null;
  totalSessions?: number | null;
  absenceFailThreshold?: number | null;
  latesPerAbsence?: number | null;
  absencePenalty?: number | null;
  latePenalty?: number | null;
  failCutoff?: number | null;
  missingAssignmentFailCount?: number | null;
  rulesNote?: string | null;
  rulesConfirmed: boolean;
  schedules: Schedule[];
  components: GradeComponent[];
}

export interface Course extends CourseRequest {
  id: number;
  weightSum: number;
}

export interface AttendanceRecord {
  id: number;
  courseId: number;
  classDate: string;
  status: AttendanceStatus;
  note?: string | null;
}

export interface AttendanceSummary {
  courseId: number;
  attended: number;
  late: number;
  absent: number;
  excused: number;
}

export interface CalendarState {
  synced: boolean;
  eventId?: string | null;
  htmlLink?: string | null;
  outdated: boolean;
}

export interface Assignment {
  id: number;
  courseId: number;
  courseName?: string | null;
  title: string;
  dueAt?: string | null;
  maxScore: number;
  score?: number | null;
  status: AssignmentStatus;
  source: 'MANUAL' | 'SYLLABUS';
  confirmed: boolean;
  calendar: CalendarState;
}

export interface AssignmentRequest {
  title: string;
  dueAt?: string | null;
  maxScore?: number | null;
  score?: number | null;
  status: AssignmentStatus;
  confirmed?: boolean;
}

export interface ComponentBreakdown {
  name: string;
  type: ComponentType;
  weight: number;
  lowerScore: number;
  upperScore: number;
  basis: string;
}

export interface GradeDiagnosis {
  status: RiskStatus;
  lowerBound: number;
  upperBound: number;
  failCutoff?: number | null;
  requiredPercentOfRemaining?: number | null;
  effectiveAbsences: number;
  remainingAbsencesBeforeF?: number | null;
  missedAssignments: number;
  components: ComponentBreakdown[];
  reasons: string[];
  warnings: string[];
}

export interface CourseDiagnosis {
  courseId: number;
  courseName: string;
  diagnosis: GradeDiagnosis;
}

export interface WeatherSnapshot {
  precipType: 'NONE' | 'RAIN' | 'RAIN_SNOW' | 'SNOW' | 'SHOWER';
  precipProbability?: number | null;
  temperature?: number | null;
  windSpeed?: number | null;
  precipitationText?: string | null;
  forecastTime: string;
  mode: ProviderMode;
  source: string;
}

export interface LatenessFactor {
  code: string;
  description: string;
  points: number;
}

export interface LatenessRisk {
  score: number;
  level: LatenessLevel;
  classStart: string;
  baseTravelMinutes: number;
  adjustedTravelMinutes: number;
  prepMinutes: number;
  bufferMinutes: number;
  recommendedDeparture: string;
  startPreparingBy: string;
  expectedLateMinutes?: number | null;
  factors: LatenessFactor[];
  weather?: WeatherSnapshot | null;
  travelSource: string;
}

export interface LatenessResponse {
  courseId: number;
  courseName: string;
  date: string;
  room?: string | null;
  risk: LatenessRisk;
}

export interface Explanation {
  summary: string;
  actions: string[];
  mode: ProviderMode;
  model?: string | null;
  error?: string | null;
}

export interface IntegrationItem {
  configured: boolean;
  connected: boolean;
  mode: string;
  detail: string;
}

export interface IntegrationStatus {
  openai: IntegrationItem;
  weather: IntegrationItem;
  travel: IntegrationItem;
  google: IntegrationItem;
}

export interface Dashboard {
  date: string;
  todayClasses: { courseId: number; courseName: string; startTime: string; endTime: string; room?: string | null }[];
  nextClassRisk?: LatenessResponse | null;
  upcomingAssignments: Assignment[];
  riskCourses: CourseDiagnosis[];
  courseCount: number;
  integrations: IntegrationStatus;
}

export interface Settings {
  displayName: string;
  homeLabel?: string | null;
  homeLat?: number | null;
  homeLng?: number | null;
  campusLabel?: string | null;
  campusLat?: number | null;
  campusLng?: number | null;
  prepMinutes: number;
  baseTravelMinutes: number;
  bufferMinutes: number;
}

/** 강의계획서 분석 결과 (백엔드 JSON 스키마 syllabus-analysis 와 동일) */
export interface SyllabusResult {
  courseName: string | null;
  professor: string | null;
  totalSessions: number | null;
  weights: {
    attendance: number | null;
    assignment: number | null;
    midterm: number | null;
    final: number | null;
    quiz: number | null;
    other: number | null;
    otherLabel: string | null;
  };
  attendanceRules: {
    absenceFailThreshold: number | null;
    latesPerAbsence: number | null;
    absencePenalty: number | null;
    latePenalty: number | null;
    description: string | null;
  };
  assignmentRules: { missingAssignmentFailCount: number | null; latePolicy: string | null };
  failCriteria: { failCutoff: number | null; description: string | null };
  assignments: { title: string; dueDate: string | null; dueTime: string | null; evidence: string | null }[];
  uncertainFields: string[];
  evidence: { field: string; quote: string }[];
}

export interface SyllabusAnalysis {
  id: number;
  courseId?: number | null;
  fileName: string;
  fileSize: number;
  status: 'PENDING_REVIEW' | 'CONFIRMED' | 'FAILED';
  mode: ProviderMode;
  model?: string | null;
  result?: SyllabusResult | null;
  validationIssues: string[];
  errorMessage?: string | null;
  createdAt: string;
  confirmedAt?: string | null;
}

export interface ExcuseRequest {
  courseId?: number | null;
  classDate: string;
  reason: string;
  minutesLate?: number | null;
  studentName?: string | null;
  studentId?: string | null;
  includeWeather: boolean;
}

export interface ExcuseResponse {
  letter: string;
  message: string;
  citedFacts: string[];
  cautions: string[];
  weather?: WeatherSnapshot | null;
  weatherCited: boolean;
  mode: ProviderMode;
  model?: string | null;
  error?: string | null;
}

export type SyncOutcome = 'CREATED' | 'UPDATED' | 'SKIPPED_DUPLICATE' | 'SKIPPED_NO_DUE_DATE' | 'SKIPPED_UNCONFIRMED' | 'FAILED';

export interface SyncItemResult {
  assignmentId: number;
  title: string;
  outcome: SyncOutcome;
  eventId?: string | null;
  htmlLink?: string | null;
  verified: boolean;
  error?: string | null;
}

export interface GoogleStatus {
  configured: boolean;
  connected: boolean;
  connectedAt?: string | null;
}

export interface ApiError {
  code: string;
  message: string;
  details: string[];
}
