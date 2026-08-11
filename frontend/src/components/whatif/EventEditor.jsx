import { EVENT_DEFINITIONS, EVENT_TYPES, createFinancialEvent } from "../../simulation/financialEvents";

function errorFor(errors, event, field, prefix) {
  return errors[`${prefix}${event.eventId}.${field}`];
}

export function EventEditor({ events, onChange, errors, disabled, idPrefix = "", errorPrefix = "" }) {
  const domPrefix = idPrefix ? `${idPrefix}-` : "";
  const inputId = (event, suffix) => `${domPrefix}${event.eventId}-${suffix}`;
  function update(index, field, value) {
    onChange(events.map((event, eventIndex) => eventIndex === index ? { ...event, [field]: value } : event));
  }

  function changeType(index, type) {
    const current = events[index];
    const replacement = createFinancialEvent(type, current.eventId);
    onChange(events.map((event, eventIndex) => eventIndex === index ? replacement : event));
  }

  function addEvent() {
    if (events.length < 20) onChange([...events, createFinancialEvent()]);
  }

  function removeEvent(index) {
    onChange(events.filter((_, eventIndex) => eventIndex !== index));
  }

  return (
    <section className="whatif-panel event-editor" aria-labelledby={`${domPrefix}event-editor-title`}>
      <div className="whatif-panel-heading">
        <div><p className="eyebrow">STRUCTURED EVENTS</p><h2 id={`${domPrefix}event-editor-title`}>직접 이벤트 입력</h2><p>종료 월도 이벤트 적용 기간에 포함됩니다. 증가는 양수, 감소는 음수로 입력합니다.</p></div>
        <span>{events.length} / 20</span>
      </div>
      {errors[`${errorPrefix}events`] && <div className="form-banner form-banner--error" role="alert">{errors[`${errorPrefix}events`]}</div>}
      <div className="event-stack">
        {events.map((event, index) => {
          const definition = EVENT_DEFINITIONS[event.eventType];
          return (
            <article className="event-card" key={event.eventId}>
              <div className="event-card-heading"><span>EVENT {String(index + 1).padStart(2, "0")}</span><button type="button" onClick={() => removeEvent(index)} disabled={disabled} aria-label={`이벤트 ${index + 1} 삭제`}>삭제</button></div>
              <div className="event-grid">
                <div className="whatif-field event-type-field">
                  <label htmlFor={inputId(event, "type")}>이벤트 유형</label>
                  <select id={inputId(event, "type")} value={event.eventType} onChange={(e) => changeType(index, e.target.value)} disabled={disabled}>
                    {EVENT_TYPES.map((type) => <option value={type} key={type}>{EVENT_DEFINITIONS[type].label}</option>)}
                  </select>
                  <p>{definition.description}</p>
                </div>
                <div className="whatif-field event-description-field">
                  <label htmlFor={inputId(event, "description")}>설명</label>
                  <input id={inputId(event, "description")} value={event.description} maxLength={200} onChange={(e) => update(index, "description", e.target.value)} disabled={disabled} />
                  {errorFor(errors, event, "description", errorPrefix) && <p className="field-error">{errorFor(errors, event, "description", errorPrefix)}</p>}
                </div>
                {definition.timing === "single" ? (
                  <div className="whatif-field">
                    <label htmlFor={inputId(event, "effective")}>적용 월</label>
                    <input id={inputId(event, "effective")} type="text" inputMode="numeric" maxLength={7} placeholder="YYYY-MM" value={event.effectiveYearMonth} onChange={(e) => update(index, "effectiveYearMonth", e.target.value)} disabled={disabled} />
                    {errorFor(errors, event, "effectiveYearMonth", errorPrefix) && <p className="field-error">{errorFor(errors, event, "effectiveYearMonth", errorPrefix)}</p>}
                  </div>
                ) : (
                  <>
                    <div className="whatif-field">
                      <label htmlFor={inputId(event, "start")}>시작 월</label>
                      <input id={inputId(event, "start")} type="text" inputMode="numeric" maxLength={7} placeholder="YYYY-MM" value={event.startYearMonth} onChange={(e) => update(index, "startYearMonth", e.target.value)} disabled={disabled} />
                      {errorFor(errors, event, "startYearMonth", errorPrefix) && <p className="field-error">{errorFor(errors, event, "startYearMonth", errorPrefix)}</p>}
                    </div>
                    <div className="whatif-field">
                      <label htmlFor={inputId(event, "end")}>종료 월 · 포함</label>
                      <input id={inputId(event, "end")} type="text" inputMode="numeric" maxLength={7} placeholder="YYYY-MM" value={event.endYearMonth} onChange={(e) => update(index, "endYearMonth", e.target.value)} disabled={disabled} />
                      {errorFor(errors, event, "endYearMonth", errorPrefix) && <p className="field-error">{errorFor(errors, event, "endYearMonth", errorPrefix)}</p>}
                    </div>
                  </>
                )}
                {definition.valueField && (
                  <div className="whatif-field">
                    <label htmlFor={inputId(event, "value")}>{definition.valueLabel}</label>
                    <div className="assumption-input"><input id={inputId(event, "value")} type="text" inputMode="decimal" value={event[definition.valueField]} onChange={(e) => update(index, definition.valueField, e.target.value)} disabled={disabled} /><span>원</span></div>
                    {definition.signed && <p>감소는 음수로 입력합니다.</p>}
                    {errorFor(errors, event, definition.valueField, errorPrefix) && <p className="field-error">{errorFor(errors, event, definition.valueField, errorPrefix)}</p>}
                  </div>
                )}
              </div>
            </article>
          );
        })}
      </div>
      <button className="event-add-button" type="button" onClick={addEvent} disabled={disabled || events.length >= 20}>+ 이벤트 추가</button>
    </section>
  );
}
