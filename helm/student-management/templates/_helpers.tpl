{{/*
Expand the name of the chart.
*/}}
{{- define "student-management.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "student-management.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels applied to all resources
*/}}
{{- define "student-management.labels" -}}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Backend selector labels
*/}}
{{- define "student-management.backendLabels" -}}
app: {{ .Values.backend.name }}
{{- end }}

{{/*
Frontend selector labels
*/}}
{{- define "student-management.frontendLabels" -}}
app: {{ .Values.frontend.name }}
{{- end }}

{{/*
MySQL selector labels
*/}}
{{- define "student-management.mysqlLabels" -}}
app: {{ .Values.mysql.name }}
{{- end }}
