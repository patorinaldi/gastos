import type { MemberResponse } from '../../types/api.ts'
import styles from './MemberList.module.css'

interface MemberListProps {
  members: MemberResponse[]
}

/**
 * Quiénes integran el hogar (RF-10, HU-07), para saber de quién son los gastos. El servidor los
 * manda ordenados por nombre y solo los del hogar de quien consulta.
 */
export function MemberList({ members }: MemberListProps) {
  return (
    <ul className={styles.list}>
      {members.map((member) => (
        <li key={member.id} className={styles.member}>
          <span className={styles.avatar} aria-hidden="true">
            {initial(member.name)}
          </span>
          <span className={styles.text}>
            <span className={styles.name}>{member.name}</span>
            <span className={styles.email}>{member.email}</span>
          </span>
        </li>
      ))}
    </ul>
  )
}

// Por punto de código y no por unidad de UTF-16: un nombre que empieza con un emoji no queda partido.
function initial(name: string): string {
  return [...name.trim()][0]?.toLocaleUpperCase('es-AR') ?? '?'
}
